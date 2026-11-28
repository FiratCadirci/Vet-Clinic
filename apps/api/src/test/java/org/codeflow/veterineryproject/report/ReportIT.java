package org.codeflow.veterineryproject.report;

import com.fasterxml.jackson.databind.JsonNode;
import org.codeflow.veterineryproject.common.test.ApiTestSupport;
import org.codeflow.veterineryproject.common.test.ReportTestData;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.codeflow.veterineryproject.common.test.ReportTestData.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Raporlar (FG-L1): bilinen veriyle kesin sayılar, şube filtresi, başka organizasyon izolasyonu, yetki (403) ve
 * aralık doğrulaması (400). Veri ve beklenen sayılar ReportTestData'da açıklanmıştır (Mart 2026).
 */
@SpringBootTest
@AutoConfigureMockMvc
class ReportIT extends ApiTestSupport {

    /** 1 Mart 00:00 – 1 Nisan 00:00 Türkiye saati, UTC ile yazılır (URL'de '+' kodlaması gerekmesin). */
    private static final String MARCH = "from=2026-02-28T21:00:00Z&to=2026-03-31T21:00:00Z";
    private static final String REPORTS = "/api/v1/reports/";

    @BeforeAll
    static void seed() {
        ReportTestData.seed(superUserDataSource());
    }

    @AfterAll
    static void cleanup() {
        ReportTestData.cleanup(superUserDataSource());
    }

    private String admin() throws Exception {
        return token(ORG_R_CODE, EMAIL_ADMIN);
    }

    private JsonNode report(String token, String name, String query) throws Exception {
        return body(getJson(token, REPORTS + name + "?" + query).andExpect(status().isOk()));
    }

    // ------------------------------------------------------------------ genel özet

    @Test
    void overview_allBranchesOfUser_exactNumbers() throws Exception {
        JsonNode o = report(admin(), "overview", MARCH);
        assertThat(o.get("examinations").asLong()).isEqualTo(6);
        assertThat(map(o.get("appointments"))).containsExactlyInAnyOrderEntriesOf(Map.of(
                "COMPLETED", 4L, "CANCELLED", 1L, "NO_SHOW", 1L, "BOOKED", 1L, "CONFIRMED", 1L,
                "REQUESTED", 1L, "IN_PROGRESS", 1L));
        assertThat(o.get("occupancyRate").decimalValue()).isEqualByComparingTo("6.0");
        assertThat(o.get("overdueVaccinations").asLong()).isEqualTo(3);
        assertThat(o.get("sales").get("count").asLong()).isEqualTo(5);
        assertThat(o.get("sales").get("total").decimalValue()).isEqualByComparingTo("2640.00");
        assertThat(o.get("sales").get("paid").decimalValue()).isEqualByComparingTo("1190.00");
        assertThat(o.get("from").asText()).isEqualTo("2026-03-01 00:00");
        assertThat(o.get("to").asText()).isEqualTo("2026-04-01 00:00");
    }

    @Test
    void overview_branchFilter_limitsToSelectedBranch() throws Exception {
        JsonNode o = report(admin(), "overview", MARCH + "&clinicId=" + R1);
        assertThat(o.get("examinations").asLong()).isEqualTo(3);
        assertThat(map(o.get("appointments")).values().stream().mapToLong(Long::longValue).sum()).isEqualTo(7);
        assertThat(o.get("occupancyRate").decimalValue()).isEqualByComparingTo("4.5");
        assertThat(o.get("overdueVaccinations").asLong()).isEqualTo(1);
        assertThat(o.get("sales").get("count").asLong()).isEqualTo(3);
        assertThat(o.get("sales").get("total").decimalValue()).isEqualByComparingTo("2300.00");
        assertThat(o.get("sales").get("paid").decimalValue()).isEqualByComparingTo("1190.00");
    }

    @Test
    void branchUser_defaultsToOwnBranches_andCannotRequestSiblingBranch() throws Exception {
        String manager = token(ORG_R_CODE, EMAIL_MANAGER_R2);
        JsonNode o = report(manager, "overview", MARCH);
        // R1 aynı klinik grubunda (RLS görür) ama kullanıcıya atanmamış: rapor kapsamına girmez.
        assertThat(o.get("examinations").asLong()).isEqualTo(2);
        assertThat(map(o.get("appointments"))).containsExactlyInAnyOrderEntriesOf(Map.of("COMPLETED", 1L, "IN_PROGRESS", 1L));
        assertThat(o.get("occupancyRate").decimalValue()).isEqualByComparingTo("8.3");
        assertThat(o.get("sales").get("total").decimalValue()).isEqualByComparingTo("220.00");
        getJson(manager, REPORTS + "overview?" + MARCH + "&clinicId=" + R1).andExpect(status().isForbidden());
        getJson(manager, REPORTS + "sales?" + MARCH + "&clinicId=" + R1).andExpect(status().isForbidden());
    }

    @Test
    void otherOrganization_neverIncluded_andCannotBeRequested() throws Exception {
        JsonNode q = report(token(ORG_Q_CODE, EMAIL_ADMIN_Q), "overview", MARCH);
        assertThat(q.get("examinations").asLong()).isEqualTo(1);
        assertThat(q.get("sales").get("total").decimalValue()).isEqualByComparingTo("11998.80");
        getJson(admin(), REPORTS + "overview?" + MARCH + "&clinicId=" + Q1).andExpect(status().isForbidden());
        // OrgR raporlarında OrgQ'nun ürünü ve kalemi görünmez.
        JsonNode stock = report(admin(), "stock-movements", MARCH);
        stock.forEach(r -> assertThat(r.get("productName").asText()).isNotEqualTo("Q Ürün"));
        JsonNode sales = report(admin(), "sales", MARCH);
        sales.get("byItem").forEach(r -> assertThat(r.get("description").asText()).isNotEqualTo("Q kalem"));
    }

    // ------------------------------------------------------------------ muayene + doluluk

    @Test
    void examinations_byDayBranchVetAndType_localDays() throws Exception {
        JsonNode rows = report(admin(), "examinations", MARCH);
        List<String> keys = list(rows, r -> r.get("day").asText() + "|" + clinic(r) + "|" + r.get("encounterType").asText()
                + "|" + r.get("count").asLong());
        assertThat(keys).containsExactlyInAnyOrder(
                "2026-03-02|R1|CONSULTATION|1", "2026-03-02|R1|VACCINATION|1", "2026-03-09|R1|CONSULTATION|1",
                "2026-03-02|R3|EMERGENCY|1", "2026-03-03|R2|CONSULTATION|1", "2026-03-10|R2|CONSULTATION|1");
        assertThat(rows.get(0).get("vetName").asText()).isNotBlank();
        rows.forEach(r -> {
            if (r.get("veterinarianId").asText().equals(VET1.toString())) {
                assertThat(r.get("vetName").asText()).isEqualTo(VET1_NAME);
            }
        });
    }

    @Test
    void occupancy_perVetAndBranch_fromWorkingHours() throws Exception {
        JsonNode rows = report(admin(), "occupancy", MARCH);
        List<String> keys = list(rows, r -> clinic(r) + "|" + r.get("availableMinutes").asLong() + "|"
                + r.get("bookedMinutes").asLong() + "|" + r.get("occupancyRate").decimalValue().stripTrailingZeros().toPlainString());
        assertThat(keys).containsExactlyInAnyOrder("R1|3120|140|4.5", "R2|1080|90|8.3", "R3|600|60|10");
    }

    @Test
    void overdueVaccinations_latestDoseOnly_excludesDeceased() throws Exception {
        JsonNode rows = report(admin(), "vaccines/overdue", "");
        assertThat(list(rows, r -> r.get("vaccinationId").asText() + "|" + r.get("daysOverdue").asLong()))
                .containsExactly(VR_KARMA_R2 + "|40", VR_KARMA_R1 + "|10", VR_KUDUZ_R3 + "|3");
        JsonNode first = rows.get(0);
        assertThat(first.get("animalName").asText()).isEqualTo("Pamuk");
        assertThat(first.get("speciesName").asText()).isEqualTo("Kedi");
        assertThat(first.get("vaccineName").asText()).isEqualTo("Karma");
        assertThat(first.get("nextDueAt").asText()).isEqualTo(today().minusDays(40).toString());
        assertThat(report(admin(), "vaccines/overdue", "clinicId=" + R1)).hasSize(1);
    }

    // ------------------------------------------------------------------ stok + satış + şube

    @Test
    void stockMovements_netQuantityPerProductAndType() throws Exception {
        JsonNode rows = report(admin(), "stock-movements", MARCH);
        assertThat(list(rows, r -> r.get("productName").asText() + "|" + r.get("transactionType").asText() + "|"
                + r.get("quantity").decimalValue().stripTrailingZeros().toPlainString() + "|" + r.get("movements").asLong()))
                .containsExactlyInAnyOrder(
                        "Amoksisilin|RECEIPT|100|1", "Amoksisilin|DISPENSE|-5|2", "Amoksisilin|TRANSFER_OUT|-10|1",
                        "Amoksisilin|TRANSFER_IN|10|1", "Kedi Maması|RECEIPT|27|2", "Kedi Maması|ADJUSTMENT|-1|1");
        JsonNode r2 = report(admin(), "stock-movements", MARCH + "&clinicId=" + R2);
        assertThat(list(r2, r -> r.get("transactionType").asText() + "|" + r.get("quantity").decimalValue().intValue()))
                .containsExactly("TRANSFER_IN|10");
        assertThat(r2.get(0).get("productType").asText()).isEqualTo("MEDICATION");
    }

    @Test
    void sales_byItemAndDay_onlyIssuedSalesInvoices() throws Exception {
        JsonNode s = report(admin(), "sales", MARCH);
        assertThat(list(s.get("byItem"), r -> r.get("lineType").asText() + "|" + r.get("description").asText() + "|"
                + text(r.get("category")) + "|" + r.get("quantity").decimalValue().stripTrailingZeros().toPlainString() + "|"
                + r.get("total").decimalValue().stripTrailingZeros().toPlainString()))
                .containsExactly(
                        "SERVICE|Genel muayene|-|2|1200", "PRODUCT|Kedi Maması|FOOD|1|990",
                        "PRODUCT|Amoksisilin|MEDICATION|6|330", "OTHER|Kayıt ücreti|-|1|120");
        assertThat(list(s.get("byDay"), r -> r.get("day").asText() + "|" + r.get("count").asLong() + "|"
                + r.get("total").decimalValue().stripTrailingZeros().toPlainString()))
                .containsExactly("2026-03-02|3|1820", "2026-03-09|1|600", "2026-03-10|1|220");
    }

    @Test
    void branchPerformance_examsAppointmentsRevenueOccupancy() throws Exception {
        JsonNode rows = report(admin(), "branches", MARCH);
        assertThat(list(rows, r -> clinic(r) + "|" + r.get("clinicName").asText() + "|" + r.get("examinations").asLong()
                + "|" + r.get("appointments").asLong() + "|" + r.get("revenue").decimalValue().stripTrailingZeros().toPlainString()
                + "|" + r.get("occupancyRate").decimalValue().stripTrailingZeros().toPlainString()))
                .containsExactlyInAnyOrder("R1|R-Merkez|3|7|2300|4.5", "R2|R-Kadıköy|2|2|220|8.3", "R3|R-Ankara|1|1|120|10");
    }

    // ------------------------------------------------------------------ yetki + doğrulama

    @Test
    void receptionist_cannotReadReports() throws Exception {
        String reception = token(ORG_R_CODE, EMAIL_RECEPTION);
        for (String name : List.of("overview", "examinations", "occupancy", "vaccines/overdue", "stock-movements", "sales", "branches")) {
            getJson(reception, REPORTS + name + "?" + MARCH).andExpect(status().isForbidden());
        }
    }

    @Test
    void invalidRange_orClinic_isBadRequest() throws Exception {
        String t = admin();
        getJson(t, REPORTS + "overview?from=2026-03-10T00:00:00Z&to=2026-03-01T00:00:00Z").andExpect(status().isBadRequest());
        getJson(t, REPORTS + "sales?from=2025-01-01T00:00:00Z&to=2026-03-01T00:00:00Z").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Rapor aralığı en fazla 1 yıl olabilir."));
        getJson(t, REPORTS + "overview?from=dun").andExpect(status().isBadRequest());
        getJson(t, REPORTS + "overview?clinicId=abc").andExpect(status().isBadRequest());
        // Aralık verilmezse son 30 gün: bugünkü açık muayene sayılır.
        JsonNode recent = report(t, "overview", "clinicId=" + R1);
        assertThat(recent.get("examinations").asLong()).isEqualTo(1);
    }

    // ------------------------------------------------------------------ yardımcılar

    private static Map<String, Long> map(JsonNode node) {
        Map<String, Long> m = new HashMap<>();
        node.fields().forEachRemaining(e -> m.put(e.getKey(), e.getValue().asLong()));
        return m;
    }

    private static List<String> list(JsonNode array, Function<JsonNode, String> f) {
        List<String> out = new java.util.ArrayList<>();
        array.forEach(n -> out.add(f.apply(n)));
        return out;
    }

    private static String clinic(JsonNode r) {
        String id = r.get("clinicId").asText();
        return id.equals(R1.toString()) ? "R1" : id.equals(R2.toString()) ? "R2" : id.equals(R3.toString()) ? "R3" : id;
    }

    private static String text(JsonNode n) {
        return n == null || n.isNull() ? "-" : n.asText();
    }
}
