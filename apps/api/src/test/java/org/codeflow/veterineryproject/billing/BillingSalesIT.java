package org.codeflow.veterineryproject.billing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.codeflow.veterineryproject.common.event.DomainEvents.InvoiceIssuedEvent;
import org.codeflow.veterineryproject.common.test.ApiTestSupport;
import org.codeflow.veterineryproject.common.test.BillingTestData;
import org.codeflow.veterineryproject.common.test.ClinicalTestData;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.codeflow.veterineryproject.common.test.BillingTestData.*;
import static org.codeflow.veterineryproject.common.test.TenantTestData.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Satış ve faturalama: FG-G1 (kalemler, KDV/indirim/toplam), FG-G2 (atomik kesim), FG-G3 (numara sırası),
 * FG-G4 (tahsilat), FG-G5 (PDF), FG-K4 (komisyon + Strategy), FOG-3 (eşzamanlı kesim), şube/organizasyon kapsamı
 * ve rol yetkisi. Gerçek PostgreSQL, vet_app rolü (RLS).
 */
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
class BillingSalesIT extends ApiTestSupport {

    private static final String S1 = BRANCH_A1_S1.toString();
    private static final String URL = "/api/v1/billing/invoices";
    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Europe/Istanbul"));

    @Autowired
    private ApplicationEvents events;

    @BeforeAll
    static void seed() {
        BillingTestData.seed(superUserDataSource());
    }

    @AfterAll
    static void cleanup() {
        BillingTestData.cleanup(superUserDataSource());
    }

    // ------------------------------------------------------------------ FG-G1 kalemler ve tutarlar

    /** DB'nin ürettiği satır tutarları, Java LinePricing ve web önizlemesiyle aynı vektörlerde birebir aynıdır. */
    @Test
    void lineAmountsGeneratedByDatabaseMatchSharedPricingVectors() throws Exception {
        String t = token(ORG_A_CODE, EMAIL_SALES_A1);
        JsonNode vectors = vectors();
        List<Map<String, Object>> lines = new ArrayList<>();
        vectors.get("lines").forEach(v -> lines.add(other(v.get("name").asText(), v)));

        JsonNode inv = body(create(t, PATIENT_A1_S1, lines).andExpect(status().isCreated()));

        JsonNode got = inv.get("lines");
        assertThat(got).hasSize(lines.size());
        for (int i = 0; i < got.size(); i++) {
            JsonNode e = vectors.get("lines").get(i).get("expected");
            JsonNode l = got.get(i);
            String name = l.get("description").asText();
            assertThat(l.get("grossAmount").decimalValue()).as(name).isEqualByComparingTo(dec(e, "gross"));
            assertThat(l.get("discountAmount").decimalValue()).as(name).isEqualByComparingTo(dec(e, "discount"));
            assertThat(l.get("netAmount").decimalValue()).as(name).isEqualByComparingTo(dec(e, "net"));
            assertThat(l.get("taxAmount").decimalValue()).as(name).isEqualByComparingTo(dec(e, "tax"));
            assertThat(l.get("lineTotal").decimalValue()).as(name).isEqualByComparingTo(dec(e, "total"));
        }

        List<Map<String, Object>> saleLines = new ArrayList<>();
        vectors.get("sale").get("lines").forEach(v -> saleLines.add(other("Satış kalemi", v)));
        JsonNode sale = body(create(t, PATIENT_A1_S1, saleLines).andExpect(status().isCreated()));
        JsonNode e = vectors.get("sale").get("expected");
        assertThat(sale.get("grossTotal").decimalValue()).isEqualByComparingTo(dec(e, "gross"));
        assertThat(sale.get("discountTotal").decimalValue()).isEqualByComparingTo(dec(e, "discount"));
        assertThat(sale.get("taxTotal").decimalValue()).isEqualByComparingTo(dec(e, "tax"));
        assertThat(sale.path("invoice").get("grandTotal").decimalValue()).isEqualByComparingTo(dec(e, "total"));
        // subtotal = KDV hariç net = brüt − indirim
        assertThat(sale.get("subtotal").decimalValue())
                .isEqualByComparingTo(dec(e, "gross").subtract(dec(e, "discount")));
    }

    /** Hizmet, aşı, ilaç ve mama kalemleri; indirim brütü aşamaz; doğrulama 400. */
    @Test
    void saleAcceptsServiceVaccineMedicationAndFoodLines_rejectsDiscountAboveGross() throws Exception {
        String admin = token(ORG_A_CODE, EMAIL_ADMIN_A1);
        String vaccine = createProduct(admin, "Kuduz aşısı", "VACCINE", false);
        String drug = createProduct(admin, "Amoksisilin", "MEDICATION", false);
        String food = createProduct(admin, "Kedi maması", "FOOD", false);
        String t = token(ORG_A_CODE, EMAIL_SALES_A1);

        JsonNode inv = body(create(t, PATIENT_A1_S1, List.of(
                service(SERVICE_EXAM_A, "Genel muayene", "1", "400.00", "0"),
                productLine(vaccine, "Kuduz aşısı", "1", "250.00", "0", "10"),
                productLine(drug, "Amoksisilin", "2", "75.50", "1.00", "10"),
                productLine(food, "Kedi maması", "1", "899.90", "0", "20"))).andExpect(status().isCreated()));

        assertThat(inv.path("invoice").get("status").asText()).isEqualTo("DRAFT");
        assertThat(inv.get("lines")).extracting(l -> l.get("lineType").asText())
                .containsExactly("SERVICE", "PRODUCT", "PRODUCT", "PRODUCT");
        assertThat(inv.get("lines")).extracting(l -> l.path("productType").asText(null))
                .containsExactly(null, "VACCINE", "MEDICATION", "FOOD");
        // 480 + 275 + (151 - 1) * 1,10 = 165 + 899,90 * 1,20 = 1079,88 → 1999,88
        assertThat(inv.path("invoice").get("grandTotal").decimalValue()).isEqualByComparingTo("1999.88");

        create(t, PATIENT_A1_S1, List.of(line("OTHER", null, null, "Fazla indirim", "1", "10.00", "10.01", "20")))
                .andExpect(status().isUnprocessableEntity());
        create(t, PATIENT_A1_S1, List.of(line("OTHER", null, null, "", "1", "10.00", "0", "20")))
                .andExpect(status().isBadRequest());
        create(t, PATIENT_A1_S1, List.of(line("OTHER", null, null, "Negatif", "0", "10.00", "0", "20")))
                .andExpect(status().isBadRequest());
    }

    /** Taslakta kalemler topluca değiştirilir; kesimden sonra değiştirilemez. */
    @Test
    void draftLinesCanBeReplacedInBulk_butNotAfterIssue() throws Exception {
        String t = token(ORG_A_CODE, EMAIL_SALES_A1);
        String id = id(create(t, PATIENT_A1_S1, List.of(other("Eski kalem", "1", "10.00"))));

        JsonNode replaced = body(replaceLines(t, id, List.of(other("Muayene", "1", "300.00"),
                line("OTHER", null, null, "Pansuman", "2", "50.00", "10.00", "20"))).andExpect(status().isOk()));
        assertThat(replaced.get("lines")).extracting(l -> l.get("description").asText())
                .containsExactly("Muayene", "Pansuman");
        // 300 * 1,20 + (100 - 10) * 1,20 = 360 + 108
        assertThat(replaced.path("invoice").get("grandTotal").decimalValue()).isEqualByComparingTo("468.00");

        replaceLines(t, id, List.of()).andExpect(status().isBadRequest());
        issue(t, id).andExpect(status().isOk());
        replaceLines(t, id, List.of(other("Sonradan", "1", "1.00"))).andExpect(status().isUnprocessableEntity());
        postJson(t, S1, URL + "/" + id + "/lines", other("Sonradan", "1", "1.00")).andExpect(status().isUnprocessableEntity());
    }

    // ------------------------------------------------------------------ FG-G3 numara

    @Test
    void invoiceNumberIsAssignedAtIssue_sequentialAndGaplessPerOrganization() throws Exception {
        String t = token(ORG_A_CODE, EMAIL_SALES_A1);
        String a = id(create(t, PATIENT_A1_S1, List.of(other("A", "1", "10.00"))));
        String b = id(create(t, PATIENT_A1_S1, List.of(other("B", "1", "10.00"))));
        String voided = id(create(t, PATIENT_A1_S1, List.of(other("İptal", "1", "10.00"))));
        assertThat(invoiceNo(t, a)).startsWith("F-TASLAK-");

        postJson(t, S1, URL + "/" + voided + "/void", Map.of()).andExpect(status().isOk());
        long first = number(body(issue(t, b).andExpect(status().isOk())));
        // Başka organizasyonun kesimi bu organizasyonun sayacını etkilemez.
        String other = token(ORG_B_CODE, EMAIL_SALES_B1);
        String bOrg = id(postJson(other, BRANCH_B1_S1.toString(), URL, Map.of("clientId", PATIENT_B1_S1,
                "lines", List.of(other("B org", "1", "5.00")))).andExpect(status().isCreated()));
        issue(other, BRANCH_B1_S1.toString(), bOrg).andExpect(status().isOk());
        long second = number(body(issue(t, a).andExpect(status().isOk())));

        assertThat(second).isEqualTo(first + 1);
        assertThat(invoiceNo(t, b)).isEqualTo("F-%06d".formatted(first));
        assertThat(invoiceNo(t, voided)).startsWith("F-TASLAK-");
        // Kesim sonrası olay: yalnız kimlik taşır.
        assertThat(events.stream(InvoiceIssuedEvent.class))
                .anyMatch(ev -> ev.invoiceId().toString().equals(a) && ev.organizationId().equals(ORG_A)
                        && ev.clientId().equals(PATIENT_A1_S1) && ev.clinicId().equals(BRANCH_A1_S1));
    }

    // ------------------------------------------------------------------ FG-G2 atomik kesim

    @Test
    void insufficientStock_returns409_andInvoiceCommissionStockAndCounterStayUnchanged() throws Exception {
        String admin = token(ORG_A_CODE, EMAIL_ADMIN_A1);
        String food = createProduct(admin, "Köpek maması 15 kg", "FOOD", true);
        receive(admin, food, 2, "MM-1", TODAY.plusDays(60));
        String t = token(ORG_A_CODE, EMAIL_SALES_A1);
        String id = id(create(t, PATIENT_A1_S1, List.of(service(SERVICE_EXAM_A, "Genel muayene", "1", "400.00", "0"),
                productLine(food, "Köpek maması 15 kg", "3", "1200.00", "0", "20"))));
        Snapshot before = snapshot(id, food);

        issue(t, id).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("Yetersiz stok: Köpek maması")));

        assertThat(snapshot(id, food)).isEqualTo(before);
        assertThat(events.stream(InvoiceIssuedEvent.class)).noneMatch(ev -> ev.invoiceId().toString().equals(id));

        // Lot elle seçilmiş ve lot yetmiyorsa son kararı DB stok guard'ı verir: yine 409 ve hiçbir şey değişmez.
        String lotId = lotId(admin, food, "MM-1");
        String manual = id(create(t, PATIENT_A1_S1, List.of(lotLine(food, lotId, "3"))));
        Snapshot manualBefore = snapshot(manual, food);
        issue(t, manual).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
        assertThat(snapshot(manual, food)).isEqualTo(manualBefore);

        // Stok yetince aynı taslak kesilir.
        receive(admin, food, 5, "MM-2", TODAY.plusDays(120));
        issue(t, id).andExpect(status().isOk());
        assertThat(onHand(food)).isEqualByComparingTo("4");
    }

    /** FEFO: lotu seçilmemiş satır SKT sırasıyla lot satırlarına bölünür; indirim ve toplam korunur. */
    @Test
    void issueSplitsLotTrackedLineByFefo_totalsPreserved() throws Exception {
        String admin = token(ORG_A_CODE, EMAIL_ADMIN_A1);
        String food = createProduct(admin, "Yavru maması", "FOOD", true);
        receive(admin, food, 5, "LATE", TODAY.plusDays(200));
        receive(admin, food, 2, "SOON", TODAY.plusDays(20));
        String t = token(ORG_A_CODE, EMAIL_SALES_A1);
        JsonNode draft = body(create(t, PATIENT_A1_S1,
                List.of(productLine(food, "Yavru maması", "3", "100.00", "3.00", "20"))).andExpect(status().isCreated()));
        BigDecimal draftTotal = draft.path("invoice").get("grandTotal").decimalValue();

        JsonNode issued = body(issue(t, id(draft)).andExpect(status().isOk()));

        JsonNode lines = issued.get("lines");
        assertThat(lines).extracting(l -> l.get("lotNumber").asText()).containsExactly("SOON", "LATE");
        assertThat(lines).extracting(l -> l.get("quantity").decimalValue().intValue()).containsExactly(2, 1);
        assertThat(lines).extracting(l -> l.get("discountAmount").decimalValue().stripTrailingZeros().toPlainString())
                .containsExactly("2", "1");
        assertThat(issued.path("invoice").get("grandTotal").decimalValue()).isEqualByComparingTo(draftTotal)
                .isEqualByComparingTo("356.40");
        assertThat(onHand(food)).isEqualByComparingTo("4");
    }

    // ------------------------------------------------------------------ FG-K4 komisyon

    @Test
    void commission_foodAndMedicationLinesCharged_serviceVaccineAndFreeLinesNot() throws Exception {
        BillingTestData.subscriptionA(superUserDataSource(), null);
        String admin = token(ORG_A_CODE, EMAIL_ADMIN_A1);
        String food = createProduct(admin, "Komisyon maması", "FOOD", false);
        String drug = createProduct(admin, "Komisyon ilacı", "MEDICATION", false);
        String vaccine = createProduct(admin, "Komisyon aşısı", "VACCINE", false);
        receive(admin, food, 10, null, null);
        receive(admin, drug, 10, null, null);
        receive(admin, vaccine, 10, null, null);
        String t = token(ORG_A_CODE, EMAIL_SALES_A1);
        String id = id(create(t, PATIENT_A1_S1, List.of(
                productLine(food, "Komisyon maması", "2", "100.00", "10.00", "20"),
                productLine(drug, "Komisyon ilacı", "1", "50.00", "0", "10"),
                productLine(vaccine, "Komisyon aşısı", "1", "100.00", "0", "10"),
                service(SERVICE_EXAM_A, "Genel muayene", "1", "400.00", "0"),
                other("Serbest kalem", "1", "30.00"))));

        JsonNode issued = body(issue(t, id).andExpect(status().isOk()));
        assertThat(issued.get("commissionPolicy").asText()).isEqualTo("PLATFORM_RULE");

        JsonNode records = body(getJson(admin, "/api/v1/commissions?invoiceId=" + id).andExpect(status().isOk()));
        Map<String, JsonNode> byType = new HashMap<>();
        records.get("content").forEach(r -> byType.put(r.get("productType").asText(), r));
        assertThat(byType).containsOnlyKeys("FOOD", "MEDICATION");
        // Matrah KDV hariç net: mama 200 - 10 = 190 × %5; ilaç 50 × %3
        assertThat(byType.get("FOOD").get("eligibleAmount").decimalValue()).isEqualByComparingTo("190.00");
        assertThat(byType.get("FOOD").get("commissionAmount").decimalValue()).isEqualByComparingTo("9.50");
        assertThat(byType.get("MEDICATION").get("commissionAmount").decimalValue()).isEqualByComparingTo("1.50");
        assertThat(byType.get("FOOD").get("invoiceNo").asText()).isEqualTo(issued.path("invoice").get("invoiceNo").asText());

        // Komisyon kayıtlarını görmek commissions.read ister; resepsiyonda yoktur.
        getJson(t, "/api/v1/commissions?invoiceId=" + id).andExpect(status().isForbidden());
        // Başka organizasyon bu kayıtları göremez.
        JsonNode otherOrg = body(getJson(token(ORG_B_CODE, EMAIL_SALES_B1), "/api/v1/billing/invoices/" + id)
                .andExpect(status().isNotFound()));
        assertThat(otherOrg.toString()).doesNotContain("9.50");
    }

    /** Strategy seçimi uçtan uca: deneme aboneliğinde komisyon yazılmaz, aktife geçince yazılır. */
    @Test
    void commission_trialSubscriptionWaived_activeSubscriptionCharged() throws Exception {
        String admin = token(ORG_A_CODE, EMAIL_ADMIN_A1);
        String food = createProduct(admin, "Deneme maması", "FOOD", false);
        receive(admin, food, 10, null, null);
        String t = token(ORG_A_CODE, EMAIL_SALES_A1);
        try {
            BillingTestData.subscriptionA(superUserDataSource(), "TRIAL");
            String trial = id(create(t, PATIENT_A1_S1, List.of(productLine(food, "Deneme maması", "1", "100.00", "0", "20"))));
            JsonNode trialIssued = body(issue(t, trial).andExpect(status().isOk()));
            assertThat(trialIssued.get("commissionPolicy").asText()).isEqualTo("NO_COMMISSION");
            assertThat(commissionCount(trial)).isZero();
            assertThat(onHand(food)).isEqualByComparingTo("9");

            BillingTestData.subscriptionA(superUserDataSource(), "ACTIVE");
            String active = id(create(t, PATIENT_A1_S1, List.of(productLine(food, "Deneme maması", "1", "100.00", "0", "20"))));
            JsonNode activeIssued = body(issue(t, active).andExpect(status().isOk()));
            assertThat(activeIssued.get("commissionPolicy").asText()).isEqualTo("PLATFORM_RULE");
            assertThat(commissionCount(active)).isEqualTo(1);
            assertThat(ClinicalTestData.text(superUserDataSource(),
                    "SELECT commission_amount::text FROM vet.commission_records WHERE invoice_id = ?::uuid", active))
                    .isEqualTo("5.00");
        } finally {
            BillingTestData.subscriptionA(superUserDataSource(), null);
        }
    }

    // ------------------------------------------------------------------ FOG-3 eşzamanlılık

    /** Son iki birimi dört paralel kesim ister: yalnız ikisi kesilir; stok, komisyon ve numaralar tutarlı kalır. */
    @Test
    void concurrentIssuesOfLastUnits_onlyAvailableQuantitySucceeds_stockCommissionAndNumbersConsistent() throws Exception {
        String admin = token(ORG_A_CODE, EMAIL_ADMIN_A1);
        String drug = createProduct(admin, "Son kutu ilaç", "MEDICATION", false);
        receive(admin, drug, 2, null, null);
        String t = token(ORG_A_CODE, EMAIL_SALES_A1);
        List<String> drafts = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            drafts.add(id(create(t, PATIENT_A1_S1, List.of(productLine(drug, "Son kutu ilaç", "1", "80.00", "0", "10")))));
        }
        long counterBefore = invoiceCounter();

        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        List<Integer> statuses = new ArrayList<>();
        try {
            List<Callable<Integer>> jobs = new ArrayList<>();
            for (String id : drafts) {
                jobs.add(() -> {
                    start.await();
                    return issue(t, id).andReturn().getResponse().getStatus();
                });
            }
            List<Future<Integer>> futures = new ArrayList<>();
            for (Callable<Integer> job : jobs) {
                futures.add(pool.submit(job));
            }
            start.countDown();
            for (Future<Integer> f : futures) {
                statuses.add(f.get());
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(statuses).containsOnly(200, 409);
        assertThat(statuses.stream().filter(s -> s == 200).count()).isEqualTo(2);
        assertThat(onHand(drug)).isEqualByComparingTo("0");
        List<String> issuedNos = new ArrayList<>();
        for (String id : drafts) {
            JsonNode inv = body(getJson(t, URL + "/" + id).andExpect(status().isOk()));
            String status = inv.path("invoice").get("status").asText();
            if ("ISSUED".equals(status)) {
                issuedNos.add(inv.path("invoice").get("invoiceNo").asText());
                assertThat(commissionCount(id)).isEqualTo(1);
            } else {
                assertThat(status).isEqualTo("DRAFT");
                assertThat(commissionCount(id)).isZero();
            }
        }
        // Sayaç yalnız başarılı kesim kadar ilerler; numaralar ardışıktır (boşluk yok).
        assertThat(invoiceCounter()).isEqualTo(counterBefore + 2);
        assertThat(issuedNos).containsExactlyInAnyOrder("F-%06d".formatted(counterBefore + 1),
                "F-%06d".formatted(counterBefore + 2));
    }

    // ------------------------------------------------------------------ FG-G4 tahsilat

    @Test
    void payments_partialThenPaid_overpaymentAndDraftPaymentRejected() throws Exception {
        String t = token(ORG_A_CODE, EMAIL_SALES_A1);
        String id = id(create(t, PATIENT_A1_S1, List.of(other("Muayene", "1", "100.00"))));
        pay(t, id, "10").andExpect(status().isUnprocessableEntity());
        issue(t, id).andExpect(status().isOk());

        JsonNode part = body(pay(t, id, "50").andExpect(status().isCreated()));
        assertThat(part.path("invoice").get("status").asText()).isEqualTo("PARTIALLY_PAID");
        assertThat(part.path("invoice").get("balance").decimalValue()).isEqualByComparingTo("70.00");
        pay(t, id, "70.01").andExpect(status().isUnprocessableEntity());
        JsonNode paid = body(pay(t, id, "70.00").andExpect(status().isCreated()));
        assertThat(paid.path("invoice").get("status").asText()).isEqualTo("PAID");
        assertThat(paid.path("invoice").get("balance").decimalValue()).isEqualByComparingTo("0");
        assertThat(paid.get("payments")).hasSize(2);
        pay(t, id, "0.01").andExpect(status().isUnprocessableEntity());
        // Liste birden çok durumla süzülebilir (web "Kesildi" filtresi).
        JsonNode issuedList = body(getJson(t, URL + "?status=ISSUED&status=PARTIALLY_PAID&status=PAID&size=100")
                .andExpect(status().isOk()));
        assertThat(issuedList.get("content")).anyMatch(i -> i.get("id").asText().equals(id));
        JsonNode drafts = body(getJson(t, URL + "?status=DRAFT&size=100").andExpect(status().isOk()));
        assertThat(drafts.get("content")).noneMatch(i -> i.get("id").asText().equals(id));
        postJson(t, S1, URL + "/" + id + "/payments", Map.of("amount", 0, "paymentMethod", "CASH"))
                .andExpect(status().isBadRequest());
        // Kesilmiş fatura iptal edilemez (yalnız taslak).
        postJson(t, S1, URL + "/" + id + "/void", Map.of()).andExpect(status().isUnprocessableEntity());
    }

    // ------------------------------------------------------------------ FG-G5 PDF

    @Test
    void pdf_isApplicationPdfWithTurkishCharacters_andOtherOrganizationGets404() throws Exception {
        String t = token(ORG_A_CODE, EMAIL_SALES_A1);
        String id = id(create(t, PATIENT_A1_S1, List.of(other("Çiğdem şampuanı İğne ölçümü", "2", "45.50"))));
        issue(t, id).andExpect(status().isOk());

        MvcResult result = mvc.perform(get(URL + "/" + id + "/pdf").header("Authorization", "Bearer " + t))
                .andExpect(status().isOk()).andReturn();

        assertThat(result.getResponse().getContentType()).isEqualTo(MediaType.APPLICATION_PDF_VALUE);
        assertThat(result.getResponse().getHeader("Content-Disposition")).contains("fatura-F-");
        byte[] pdf = result.getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        String text = new PdfTextExtractor(new PdfReader(pdf)).getTextFromPage(1);
        assertThat(text).contains("Fatura / Fiş taslağı", "Çiğdem şampuanı İğne ölçümü", "Genel toplam", "KDV",
                "109,20 TL");

        mvc.perform(get(URL + "/" + id + "/pdf").header("Authorization", "Bearer " + token(ORG_B_CODE, EMAIL_SALES_B1)))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ yetki ve kapsam

    @Test
    void receptionCanSell_veterinarianGets403() throws Exception {
        String vet = token(ORG_A_CODE, EMAIL_VET_A1);
        create(vet, PATIENT_A1_S1, List.of(other("Muayene", "1", "10.00"))).andExpect(status().isForbidden());
        getJson(vet, URL).andExpect(status().isForbidden());
        getJson(vet, "/api/v1/commissions").andExpect(status().isForbidden());

        String t = token(ORG_A_CODE, EMAIL_SALES_A1);
        String id = id(create(t, PATIENT_A1_S1, List.of(other("Muayene", "1", "10.00"))));
        issue(vet, id).andExpect(status().isForbidden());
        issue(t, id).andExpect(status().isOk());
        // Yazma uçlarında şube başlığı zorunlu.
        postJson(t, URL, Map.of("clientId", PATIENT_A1_S1, "lines", List.of(other("x", "1", "1.00"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void branchScope_otherGroupCannotSeeSale_otherOrganizationGets404() throws Exception {
        String t = token(ORG_A_CODE, EMAIL_SALES_A1);
        String id = id(create(t, PATIENT_A1_S1, List.of(other("Kapsam", "1", "10.00"))));

        String otherGroup = token(ORG_A_CODE, EMAIL_SALES_A2);
        getJson(otherGroup, BRANCH_A2_S1.toString(), URL + "/" + id).andExpect(status().isNotFound());
        issue(otherGroup, BRANCH_A2_S1.toString(), id).andExpect(status().isNotFound());
        JsonNode list = body(getJson(otherGroup, BRANCH_A2_S1.toString(), URL + "?size=100").andExpect(status().isOk()));
        list.get("content").forEach(inv -> assertThat(inv.get("id").asText()).isNotEqualTo(id));

        String otherOrg = token(ORG_B_CODE, EMAIL_SALES_B1);
        String b1 = BRANCH_B1_S1.toString();
        getJson(otherOrg, b1, URL + "/" + id).andExpect(status().isNotFound());
        issue(otherOrg, b1, id).andExpect(status().isNotFound());
        replaceLines(otherOrg, b1, id, List.of(other("x", "1", "1.00"))).andExpect(status().isNotFound());
        postJson(otherOrg, b1, URL + "/" + id + "/payments", Map.of("amount", 1, "paymentMethod", "CASH"))
                .andExpect(status().isNotFound());
        // Başka organizasyonun müşterisine satış açılamaz.
        postJson(otherOrg, b1, URL, Map.of("clientId", PATIENT_A1_S1, "lines", List.of(other("x", "1", "1.00"))))
                .andExpect(status().isNotFound());

        // Kendi kaydı hâlâ taslak ve değişmemiş.
        assertThat(body(getJson(t, URL + "/" + id)).path("invoice").get("status").asText()).isEqualTo("DRAFT");
    }

    // ------------------------------------------------------------------ yardımcılar

    private record Snapshot(String status, String invoiceNo, long lines, long commissions, BigDecimal onHand,
                            long counter, long stockMovements) {
    }

    private Snapshot snapshot(String invoiceId, String productId) {
        javax.sql.DataSource su = superUserDataSource();
        return new Snapshot(
                ClinicalTestData.text(su, "SELECT status FROM vet.invoices WHERE id = ?::uuid", invoiceId),
                ClinicalTestData.text(su, "SELECT invoice_no FROM vet.invoices WHERE id = ?::uuid", invoiceId),
                ClinicalTestData.count(su, "SELECT count(*) FROM vet.invoice_lines WHERE invoice_id = ?::uuid", invoiceId),
                commissionCount(invoiceId), onHand(productId), invoiceCounter(),
                ClinicalTestData.count(su, "SELECT count(*) FROM vet.inventory_transactions WHERE reference_id = ?::uuid", invoiceId));
    }

    private static long commissionCount(String invoiceId) {
        return ClinicalTestData.count(superUserDataSource(),
                "SELECT count(*) FROM vet.commission_records WHERE invoice_id = ?::uuid", invoiceId);
    }

    private static long invoiceCounter() {
        return ClinicalTestData.count(superUserDataSource(), "SELECT coalesce(max(last_value), 0) FROM vet.sequence_counters "
                + "WHERE organization_id = ? AND counter_key = 'INVOICE'", ORG_A);
    }

    private static BigDecimal onHand(String productId) {
        String v = ClinicalTestData.text(superUserDataSource(), """
                SELECT coalesce(sum(CASE WHEN l.direction = 'IN' THEN l.quantity ELSE -l.quantity END), 0)::text
                FROM vet.inventory_transaction_lines l
                JOIN vet.inventory_transactions tx ON tx.organization_id = l.organization_id AND tx.id = l.transaction_id
                JOIN vet.inventory_locations loc ON loc.organization_id = tx.organization_id AND loc.id = tx.location_id
                WHERE tx.status = 'POSTED' AND l.product_id = ?::uuid AND loc.clinic_id = ?
                """, productId, BRANCH_A1_S1);
        return new BigDecimal(v);
    }

    private ResultActions create(String token, UUID clientId, List<Map<String, Object>> lines) throws Exception {
        return postJson(token, S1, URL, Map.of("clientId", clientId, "animalId", clientId, "lines", lines));
    }

    private ResultActions issue(String token, String id) throws Exception {
        return issue(token, S1, id);
    }

    private ResultActions issue(String token, String clinic, String id) throws Exception {
        return postJson(token, clinic, URL + "/" + id + "/issue", Map.of());
    }

    private ResultActions pay(String token, String id, String amount) throws Exception {
        return postJson(token, S1, URL + "/" + id + "/payments", Map.of("amount", amount, "paymentMethod", "CASH"));
    }

    private ResultActions replaceLines(String token, String id, List<Map<String, Object>> lines) throws Exception {
        return replaceLines(token, S1, id, lines);
    }

    private ResultActions replaceLines(String token, String clinic, String id, List<Map<String, Object>> lines)
            throws Exception {
        return mvc.perform(put(URL + "/" + id + "/lines").header("Authorization", "Bearer " + token)
                .header("X-Clinic-Id", clinic).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("lines", lines))));
    }

    private String id(ResultActions created) throws Exception {
        return id(body(created.andExpect(status().isCreated())));
    }

    private static String id(JsonNode invoice) {
        return invoice.path("invoice").get("id").asText();
    }

    private String invoiceNo(String token, String id) throws Exception {
        return body(getJson(token, URL + "/" + id).andExpect(status().isOk())).path("invoice").get("invoiceNo").asText();
    }

    private static long number(JsonNode invoice) {
        String no = invoice.path("invoice").get("invoiceNo").asText();
        assertThat(no).matches("F-\\d{6}");
        return Long.parseLong(no.substring(2));
    }

    private static Map<String, Object> line(String type, UUID serviceId, String productId, String description,
                                            String qty, String price, String discount, String tax) {
        Map<String, Object> m = new HashMap<>();
        m.put("lineType", type);
        m.put("serviceId", serviceId);
        m.put("productId", productId);
        m.put("description", description);
        m.put("quantity", qty);
        m.put("unitPrice", price);
        m.put("discountAmount", discount);
        m.put("taxRate", tax);
        return m;
    }

    private static Map<String, Object> other(String description, String qty, String price) {
        return line("OTHER", null, null, description, qty, price, "0", "20");
    }

    private static Map<String, Object> other(String description, JsonNode v) {
        return line("OTHER", null, null, description, v.get("quantity").asText(), v.get("unitPrice").asText(),
                v.get("discountAmount").asText(), v.get("taxRate").asText());
    }

    private static Map<String, Object> service(UUID serviceId, String description, String qty, String price, String discount) {
        return line("SERVICE", serviceId, null, description, qty, price, discount, "20");
    }

    private static Map<String, Object> productLine(String productId, String description, String qty, String price,
                                                   String discount, String tax) {
        return line("PRODUCT", null, productId, description, qty, price, discount, tax);
    }

    private static Map<String, Object> lotLine(String productId, String lotId, String qty) {
        Map<String, Object> m = productLine(productId, "Elle seçilmiş lot", qty, "10.00", "0", "20");
        m.put("lotId", lotId);
        return m;
    }

    private String createProduct(String token, String name, String type, boolean tracksLot) throws Exception {
        String unique = name + " " + UUID.randomUUID().toString().substring(0, 8);
        return body(postJson(token, S1, "/api/v1/products", Map.of("name", unique, "productType", type,
                "baseUnit", "adet", "tracksLot", tracksLot, "salePrice", "100.00", "taxRate", 20))
                .andExpect(status().isCreated())).get("id").asText();
    }

    private void receive(String token, String productId, int qty, String lot, LocalDate expires) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("productId", productId);
        body.put("quantity", qty);
        body.put("lotNumber", lot);
        body.put("expiresAt", expires == null ? null : expires.toString());
        postJson(token, S1, "/api/v1/inventory/receipts", body).andExpect(status().isCreated());
    }

    private String lotId(String token, String productId, String lotNumber) throws Exception {
        JsonNode stock = body(getJson(token, S1, "/api/v1/inventory/stock").andExpect(status().isOk()));
        JsonNode items = stock.isArray() ? stock : stock.get("content");
        for (JsonNode item : items) {
            if (item.get("productId").asText().equals(productId)) {
                for (JsonNode lot : item.get("lots")) {
                    if (lotNumber.equals(lot.get("lotNumber").asText())) {
                        return lot.get("lotId").asText();
                    }
                }
            }
        }
        throw new AssertionError("Lot bulunamadı: " + lotNumber);
    }

    private static JsonNode vectors() throws Exception {
        try (InputStream in = BillingSalesIT.class.getResourceAsStream("/billing/pricing-vectors.json")) {
            return new ObjectMapper().readTree(in);
        }
    }

    private static BigDecimal dec(JsonNode node, String field) {
        return new BigDecimal(node.get(field).asText());
    }
}
