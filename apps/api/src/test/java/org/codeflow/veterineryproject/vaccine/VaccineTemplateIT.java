package org.codeflow.veterineryproject.vaccine;

import com.fasterxml.jackson.databind.JsonNode;
import org.codeflow.veterineryproject.common.event.DomainEvents.VaccineRecordedEvent;
import org.codeflow.veterineryproject.common.test.ApiTestSupport;
import org.codeflow.veterineryproject.common.test.ClinicalFlowTestData;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.codeflow.veterineryproject.common.test.ClinicalFlowTestData.*;
import static org.codeflow.veterineryproject.common.test.ClinicalTestData.count;
import static org.codeflow.veterineryproject.common.test.ClinicalTestData.text;
import static org.codeflow.veterineryproject.common.test.ClinicalTestData.update;
import static org.codeflow.veterineryproject.common.test.InventoryTestData.EMAIL_KEEPER_A1;
import static org.codeflow.veterineryproject.common.test.RegistrationTestData.*;
import static org.codeflow.veterineryproject.common.test.TenantTestData.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** FG-D1..D4: tür bazında şablon taslağı, hekim onayı, ürünlü aşı kaydında stok düşümü, takvim ve vade listesi. */
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
@DisplayName("Aşı şablonları, stoklu aşı kaydı ve aşı takvimi")
class VaccineTemplateIT extends ApiTestSupport {

    private static final String S1 = BRANCH_A1_S1.toString();
    private static final String PROTOCOLS = "/api/v1/vaccine-protocols";
    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Europe/Istanbul"));

    /** Bağlam önbelleğini bozmadan (ek bean yok) yayınlanan olayları kaydeder. */
    @Autowired
    ApplicationEvents events;

    @BeforeAll
    static void seed() {
        ClinicalFlowTestData.seed(superUserDataSource());
    }

    @AfterAll
    static void cleanup() {
        ClinicalFlowTestData.cleanup(superUserDataSource());
    }

    // ---- FG-D1: fabrika taslakları -------------------------------------------------------------------------------

    @Test
    @DisplayName("fabrika köpek, kedi, tavşan ve kuş için farklı onaysız taslaklar üretir")
    void generate_producesDistinctUnapprovedDraftsPerSpecies() throws Exception {
        String vet = token(ORG_A_CODE, EMAIL_VET_A1);
        Map<String, List<JsonNode>> bySpecies = new HashMap<>();
        for (String species : List.of("DOG", "CAT", "RABBIT", "BIRD")) {
            bySpecies.put(species, protocols(vet, species));
        }

        assertThat(bySpecies.values()).allSatisfy(list -> assertThat(list).isNotEmpty());
        // Diğer testler bazı köpek/kedi protokollerini onaylar; tavşan ve kuş taslakları hiç onaylanmaz.
        assertThat(bySpecies.get("RABBIT")).allMatch(p -> p.get("status").asText().equals("DRAFT") && p.get("approvedAt").isNull());
        assertThat(bySpecies.get("BIRD")).allMatch(p -> p.get("status").asText().equals("DRAFT") && p.get("approvedAt").isNull());
        JsonNode dogCore = byCode(bySpecies.get("DOG"), "DOG-CORE");
        assertThat(dogCore.get("status").asText()).isEqualTo("DRAFT");
        assertThat(dogCore.get("doses")).hasSize(3);
        assertThat(dogCore.get("doses").get(0).get("recommendedAgeDays").asInt()).isEqualTo(56);
        assertThat(dogCore.get("doses").get(1).get("intervalFromPreviousDays").asInt()).isEqualTo(21);
        assertThat(dogCore.get("boosterIntervalDays").asInt()).isEqualTo(365);
        assertThat(byCode(bySpecies.get("CAT"), "CAT-CORE").get("doses")).hasSize(2);
        assertThat(codes(bySpecies.get("RABBIT"))).contains("RABBIT-MYXO", "RABBIT-RHD");
        assertThat(byCode(bySpecies.get("BIRD"), "BIRD-PARASITE").get("boosterIntervalDays").asInt()).isEqualTo(180);
        assertThat(codes(bySpecies.get("DOG"))).doesNotContainAnyElementsOf(codes(bySpecies.get("CAT")));

        JsonNode again = body(postJson(vet, S1, PROTOCOLS + "/generate", Map.of("speciesId", speciesId("DOG")))
                .andExpect(status().isCreated()));
        assertThat(again).isEmpty();
        postJson(vet, S1, PROTOCOLS + "/generate", Map.of("speciesId", speciesId("OTHER"))).andExpect(status().isUnprocessableEntity());
        postJson(token(ORG_A_CODE, EMAIL_RECEPTION_A1), S1, PROTOCOLS + "/generate", Map.of("speciesId", speciesId("DOG")))
                .andExpect(status().isForbidden());
        postJson(vet, S1, PROTOCOLS + "/generate", Map.of()).andExpect(status().isBadRequest());
    }

    // ---- FG-D2: onay -----------------------------------------------------------------------------------------------

    @Test
    @DisplayName("onaysız şablon kalemiyle aşı kaydedilemez")
    void record_withUnapprovedTemplateDoseIsRejected() throws Exception {
        String vet = token(ORG_A_CODE, EMAIL_VET_A1);
        JsonNode draft = byCode(protocols(vet, "DOG"), "DOG-CORONA");
        String encounter = openEncounter(vet, DOG);

        postJson(vet, S1, "/api/v1/vaccinations", Map.of("encounterId", encounter, "protocolDoseId", doseId(draft, 1)))
                .andExpect(status().isUnprocessableEntity());
        assertThat(count(superUserDataSource(), "SELECT count(*) FROM vet.vaccine_records WHERE encounter_id = ?",
                UUID.fromString(encounter))).isZero();
    }

    @Test
    @DisplayName("kalem değişikliği onayı sıfırlar: yeni taslak sürüm oluşur, eski sürüm kullanılamaz")
    void update_changingItemsResetsApproval() throws Exception {
        String vet = token(ORG_A_CODE, EMAIL_VET_A1);
        JsonNode rabies = byCode(protocols(vet, "DOG"), "DOG-RABIES");
        JsonNode approved = body(postJson(vet, S1, PROTOCOLS + "/" + rabies.get("id").asText() + "/approve", Map.of())
                .andExpect(status().isOk()));
        assertThat(approved.get("status").asText()).isEqualTo("APPROVED");
        String encounter = openEncounter(vet, DOG);
        String oldDose = doseId(approved, 1);
        record(vet, encounter, Map.of("protocolDoseId", oldDose));

        JsonNode changed = body(putJson(vet, PROTOCOLS + "/" + approved.get("id").asText(), Map.of("name", "Kuduz",
                "boosterIntervalDays", 730, "doses", List.of(Map.of("doseNumber", 1, "vaccineName", "Kuduz", "recommendedAgeDays", 84))))
                .andExpect(status().isOk()));

        assertThat(changed.get("status").asText()).isEqualTo("DRAFT");
        assertThat(changed.get("approvedAt").isNull()).isTrue();
        assertThat(changed.get("versionNo").asInt()).isEqualTo(approved.get("versionNo").asInt() + 1);
        assertThat(body(getJson(vet, PROTOCOLS + "/" + approved.get("id").asText())).get("status").asText()).isEqualTo("RETIRED");
        postJson(vet, S1, "/api/v1/vaccinations", Map.of("encounterId", encounter, "protocolDoseId", oldDose))
                .andExpect(status().isUnprocessableEntity());
        postJson(vet, S1, "/api/v1/vaccinations", Map.of("encounterId", encounter, "protocolDoseId", doseId(changed, 1)))
                .andExpect(status().isUnprocessableEntity());

        postJson(vet, S1, PROTOCOLS + "/" + changed.get("id").asText() + "/approve", Map.of()).andExpect(status().isOk());
        record(vet, encounter, Map.of("protocolDoseId", doseId(changed, 1)));
        putJson(token(ORG_A_CODE, EMAIL_RECEPTION_A1), PROTOCOLS + "/" + changed.get("id").asText(), Map.of("name", "x",
                "doses", List.of(Map.of("doseNumber", 1, "vaccineName", "x")))).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("kullanılmamış taslak silinir; onaylı protokol silinemez (409)")
    void delete_onlyUnusedDrafts() throws Exception {
        String vet = token(ORG_A_CODE, EMAIL_VET_A1);
        JsonNode created = body(postJson(vet, S1, PROTOCOLS, Map.of("code", "OTHER-X" + UUID.randomUUID().toString().substring(0, 4),
                "name", "Deneme", "speciesId", speciesId("OTHER"), "doses", List.of(Map.of("doseNumber", 1, "vaccineName", "Deneme"))))
                .andExpect(status().isCreated()));
        String id = created.get("id").asText();
        postJson(vet, S1, PROTOCOLS + "/" + id + "/approve", Map.of()).andExpect(status().isOk());
        deleteWith(vet, PROTOCOLS + "/" + id).andExpect(status().isConflict());

        String draft = body(putJson(vet, PROTOCOLS + "/" + id, Map.of("name", "Deneme 2",
                "doses", List.of(Map.of("doseNumber", 1, "vaccineName", "Deneme"))))).get("id").asText();
        deleteWith(vet, PROTOCOLS + "/" + draft).andExpect(status().isNoContent());
        getJson(vet, PROTOCOLS + "/" + draft).andExpect(status().isNotFound());
    }

    // ---- FG-D3: stoklu aşı kaydı ---------------------------------------------------------------------------------

    @Test
    @DisplayName("ürünle aşı stoktan FEFO ile düşer, kayda düşülen lot yazılır; yetersiz stokta 409 ve hiçbir şey yazılmaz")
    void record_withProductConsumesStockAndInsufficientStockWritesNothing() throws Exception {
        String vet = token(ORG_A_CODE, EMAIL_VET_A1);
        String keeper = token(ORG_A_CODE, EMAIL_KEEPER_A1);
        String product = body(postJson(keeper, S1, "/api/v1/products", Map.of("name", "Kuduz aşısı " + UUID.randomUUID().toString()
                        .substring(0, 4), "productType", "VACCINE", "baseUnit", "doz", "tracksLot", true, "minimumStock", 0,
                "salePrice", "300.00", "taxRate", 10)).andExpect(status().isCreated())).get("id").asText();
        receive(keeper, product, "LOT-GEC", TODAY.plusDays(300));
        receive(keeper, product, "LOT-YAKIN", TODAY.plusDays(100));
        String encounter = openEncounter(vet, DOG);

        JsonNode first = record(vet, encounter, Map.of("productId", product, "batchNumber", "ELLE-GIRILEN",
                "administeredAt", OffsetDateTime.now().minusHours(1).toString()));
        assertThat(first.get("batchNumber").asText()).isEqualTo("LOT-YAKIN");
        assertThat(first.get("vaccineProductId").asText()).isEqualTo(product);
        assertThat(first.get("lotId").isNull()).isFalse();
        assertThat(first.get("vaccineName").asText()).startsWith("Kuduz aşısı");
        assertThat(onHand(keeper, product)).isEqualTo("1");
        assertThat(text(superUserDataSource(), "SELECT count(*)::text FROM vet.inventory_transactions WHERE reference_type = 'VACCINE_RECORD' "
                + "AND reference_id = ?", UUID.fromString(first.get("id").asText()))).isEqualTo("1");
        assertThat(record(vet, encounter, Map.of("productId", product)).get("batchNumber").asText()).isEqualTo("LOT-GEC");

        long records = count(superUserDataSource(), "SELECT count(*) FROM vet.vaccine_records WHERE encounter_id = ?", UUID.fromString(encounter));
        long movements = count(superUserDataSource(), "SELECT count(*) FROM vet.inventory_transaction_lines WHERE product_id = ?", UUID.fromString(product));
        JsonNode error = body(postJson(vet, S1, "/api/v1/vaccinations", Map.of("encounterId", encounter, "productId", product))
                .andExpect(status().isConflict()));
        assertThat(error.toString()).contains("INSUFFICIENT_STOCK");
        assertThat(count(superUserDataSource(), "SELECT count(*) FROM vet.vaccine_records WHERE encounter_id = ?", UUID.fromString(encounter)))
                .isEqualTo(records);
        assertThat(count(superUserDataSource(), "SELECT count(*) FROM vet.inventory_transaction_lines WHERE product_id = ?", UUID.fromString(product)))
                .isEqualTo(movements);
        assertThat(onHand(keeper, product)).isEqualTo("0");

        String drug = body(postJson(keeper, S1, "/api/v1/products", Map.of("name", "İlaç " + UUID.randomUUID().toString().substring(0, 4),
                "productType", "MEDICATION", "baseUnit", "adet", "tracksLot", false, "minimumStock", 0, "salePrice", "1.00", "taxRate", 10))
                .andExpect(status().isCreated())).get("id").asText();
        postJson(vet, S1, "/api/v1/vaccinations", Map.of("encounterId", encounter, "productId", drug)).andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("eşzamanlı ürünlü aşı kayıtları stoğu aşmaz: fazlası 409 alır, kayıt ve hareket sayısı stokla eşittir")
    void record_concurrentStockConsumptionNeverOversells() throws Exception {
        String vet = token(ORG_A_CODE, EMAIL_VET_A1);
        String keeper = token(ORG_A_CODE, EMAIL_KEEPER_A1);
        String product = body(postJson(keeper, S1, "/api/v1/products", Map.of("name", "Karma aşı " + UUID.randomUUID().toString()
                        .substring(0, 4), "productType", "VACCINE", "baseUnit", "doz", "tracksLot", true, "minimumStock", 0,
                "salePrice", "300.00", "taxRate", 10)).andExpect(status().isCreated())).get("id").asText();
        postJson(keeper, S1, "/api/v1/inventory/receipts", Map.of("productId", product, "quantity", 3, "lotNumber", "PAR-1",
                "expiresAt", TODAY.plusDays(200).toString())).andExpect(status().isCreated());
        List<String> encounters = new java.util.ArrayList<>();
        for (int i = 0; i < 6; i++) {
            encounters.add(openEncounter(vet, PUPPY));
        }
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(6);
        try {
            List<java.util.concurrent.Callable<Integer>> jobs = encounters.stream().<java.util.concurrent.Callable<Integer>>map(e -> () ->
                    postJson(vet, S1, "/api/v1/vaccinations", Map.of("encounterId", e, "productId", product))
                            .andReturn().getResponse().getStatus()).toList();
            List<Integer> statuses = new java.util.ArrayList<>();
            for (java.util.concurrent.Future<Integer> f : pool.invokeAll(jobs)) {
                statuses.add(f.get());
            }
            assertThat(statuses).containsOnly(201, 409);
            assertThat(statuses.stream().filter(s -> s == 201)).hasSize(3);
        } finally {
            pool.shutdownNow();
        }
        assertThat(count(superUserDataSource(), "SELECT count(*) FROM vet.vaccine_records WHERE vaccine_product_id = ?", UUID.fromString(product)))
                .isEqualTo(3);
        assertThat(onHand(keeper, product)).isEqualTo("0");
    }

    @Test
    @DisplayName("aşı kaydı lot ve uygulama tarihini tutar, VaccineRecordedEvent yayınlanır")
    void record_keepsLotAndDateAndPublishesEvent() throws Exception {
        String vet = token(ORG_A_CODE, EMAIL_VET_A1);
        String encounter = openEncounter(vet, BIRD);
        OffsetDateTime given = OffsetDateTime.now().minusDays(2);

        JsonNode r = record(vet, encounter, Map.of("vaccineName", "Parazit", "batchNumber", "PB-77", "administeredAt", given.toString(),
                "nextDueAt", TODAY.plusDays(30).toString()));

        assertThat(r.get("batchNumber").asText()).isEqualTo("PB-77");
        assertThat(r.get("administeredAt").asText()).startsWith(given.atZoneSameInstant(ZoneId.of("Europe/Istanbul")).toLocalDate().toString());
        assertThat(events.stream(VaccineRecordedEvent.class)).anyMatch(v -> v.vaccineRecordId().toString().equals(r.get("id").asText())
                && v.animalId().equals(BIRD) && TODAY.plusDays(30).equals(v.nextDueAt()));
    }

    @Test
    @DisplayName("vefat etmiş hastaya aşı kaydı eklenemez (409)")
    void record_deceasedPatientIsConflict() throws Exception {
        String vet = token(ORG_A_CODE, EMAIL_VET_A1);
        String encounter = openEncounter(vet, RABBIT);
        update(superUserDataSource(), "UPDATE vet.animals SET deceased_at = now() WHERE id = ?", RABBIT);
        try {
            postJson(vet, S1, "/api/v1/vaccinations", Map.of("encounterId", encounter, "vaccineName", "Miksomatozis"))
                    .andExpect(status().isConflict());
        } finally {
            update(superUserDataSource(), "UPDATE vet.animals SET deceased_at = NULL WHERE id = ?", RABBIT);
        }
        assertThat(count(superUserDataSource(), "SELECT count(*) FROM vet.vaccine_records WHERE encounter_id = ?", UUID.fromString(encounter))).isZero();
    }

    // ---- FG-D4: takvim ve vade listesi ----------------------------------------------------------------------------

    @Test
    @DisplayName("hayvan takvimi: sonraki doz gerçekten yapılan önceki dozdan hesaplanır, geciken doz işaretlenir")
    void schedule_chainsFromActuallyGivenDose() throws Exception {
        String vet = token(ORG_A_CODE, EMAIL_VET_A1);
        JsonNode core = byCode(protocols(vet, "CAT"), "CAT-CORE");
        core = body(postJson(vet, S1, PROTOCOLS + "/" + core.get("id").asText() + "/approve", Map.of()).andExpect(status().isOk()));
        String encounter = openEncounter(vet, CAT);
        OffsetDateTime given = OffsetDateTime.now(ZoneId.of("Europe/Istanbul")).minusDays(40);
        JsonNode first = record(vet, encounter, Map.of("protocolDoseId", doseId(core, 1), "administeredAt", given.toString()));
        LocalDate givenDate = given.toLocalDate();
        assertThat(LocalDate.parse(first.get("nextDueAt").asText())).isEqualTo(givenDate.plusDays(21));

        JsonNode schedule = body(getJson(vet, "/api/v1/animals/" + CAT + "/vaccine-schedule").andExpect(status().isOk()));
        JsonNode item = StreamSupport.stream(schedule.get("items").spliterator(), false)
                .filter(i -> i.get("protocolCode").asText().equals("CAT-CORE")).findFirst().orElseThrow();
        assertThat(item.get("doses").get(0).get("status").asText()).isEqualTo("DONE");
        assertThat(item.get("doses").get(1).get("status").asText()).isEqualTo("OVERDUE");
        assertThat(LocalDate.parse(item.get("doses").get(1).get("plannedAt").asText())).isEqualTo(givenDate.plusDays(21));
        assertThat(item.get("next").get("doseNumber").asInt()).isEqualTo(2);
        assertThat(item.get("next").get("overdue").asBoolean()).isTrue();
        assertThat(schedule.get("pendingApproval").asBoolean()).isTrue();
        assertThat(schedule.get("nextDue")).isNotNull();

        getJson(token(ORG_B_CODE, EMAIL_VET_B1), "/api/v1/animals/" + CAT + "/vaccine-schedule").andExpect(status().isNotFound());
        getJson(token(ORG_A_CODE, EMAIL_VET_A2), "/api/v1/animals/" + CAT + "/vaccine-schedule").andExpect(status().isNotFound());
        getJson(token(ORG_B_CODE, EMAIL_VET_B1), PROTOCOLS + "/" + core.get("id").asText()).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("yaklaşan (N gün) ve geciken aşılar sahip bilgisiyle listelenir; başka organizasyon görmez")
    void due_listsUpcomingWithinWindowAndOverdueWithOwner() throws Exception {
        String vet = token(ORG_A_CODE, EMAIL_VET_A1);
        String name = "Vade " + UUID.randomUUID().toString().substring(0, 6);
        String overdue = record(vet, openEncounter(vet, PUPPY), Map.of("vaccineName", name + "-G",
                "administeredAt", OffsetDateTime.now().minusDays(30).toString(), "nextDueAt", TODAY.minusDays(3).toString())).get("id").asText();
        String upcoming = record(vet, openEncounter(vet, PUPPY), Map.of("vaccineName", name + "-Y",
                "nextDueAt", TODAY.plusDays(5).toString())).get("id").asText();

        JsonNode all = body(getJson(vet, "/api/v1/vaccinations/due?status=ALL&days=30&size=200").andExpect(status().isOk()));
        JsonNode row = find(all, overdue);
        assertThat(row.get("status").asText()).isEqualTo("OVERDUE");
        assertThat(row.get("ownerName").asText()).isEqualTo(OWNER_NAME);
        assertThat(row.get("ownerPhone").asText()).isEqualTo(OWNER_PHONE);
        assertThat(row.get("speciesCode").asText()).isEqualTo("DOG");
        assertThat(find(all, upcoming).get("status").asText()).isEqualTo("UPCOMING");
        assertThat(ids(vet, "/api/v1/vaccinations/due?status=UPCOMING&days=3&size=200")).doesNotContain(upcoming);
        assertThat(ids(vet, "/api/v1/vaccinations/due?status=UPCOMING&days=7&size=200")).contains(upcoming).doesNotContain(overdue);
        assertThat(ids(vet, "/api/v1/vaccinations/due?status=OVERDUE&size=200")).contains(overdue).doesNotContain(upcoming);
        assertThat(ids(token(ORG_B_CODE, EMAIL_VET_B1), "/api/v1/vaccinations/due?status=ALL&days=365&size=200"))
                .doesNotContain(overdue, upcoming);
    }

    // ---- yardımcılar ----------------------------------------------------------------------------------------------

    /** Türün fabrika taslağını (yoksa) üretir ve emekliye ayrılmamış protokollerini döner. */
    private List<JsonNode> protocols(String vet, String species) throws Exception {
        postJson(vet, S1, PROTOCOLS + "/generate", Map.of("speciesId", speciesId(species))).andExpect(status().isCreated());
        JsonNode page = body(getJson(vet, PROTOCOLS + "?size=200&speciesId=" + speciesId(species)).andExpect(status().isOk()));
        return StreamSupport.stream(page.get("content").spliterator(), false)
                .filter(p -> !p.get("status").asText().equals("RETIRED")).toList();
    }

    private static JsonNode byCode(List<JsonNode> protocols, String code) {
        return protocols.stream().filter(p -> p.get("code").asText().equals(code)).findFirst()
                .orElseThrow(() -> new AssertionError("protokol yok: " + code));
    }

    private static List<String> codes(List<JsonNode> protocols) {
        return protocols.stream().map(p -> p.get("code").asText()).toList();
    }

    private static String doseId(JsonNode protocol, int number) {
        for (JsonNode d : protocol.get("doses")) {
            if (d.get("doseNumber").asInt() == number) {
                return d.get("id").asText();
            }
        }
        throw new AssertionError("doz yok");
    }

    private String openEncounter(String vet, UUID animal) throws Exception {
        return body(postJson(vet, S1, "/api/v1/encounters", Map.of("animalId", animal.toString(), "chiefComplaint", "Aşı için geldi"))
                .andExpect(status().isCreated())).get("id").asText();
    }

    private JsonNode record(String vet, String encounter, Map<String, Object> fields) throws Exception {
        Map<String, Object> request = new HashMap<>(fields);
        request.put("encounterId", encounter);
        return body(postJson(vet, S1, "/api/v1/vaccinations", request).andExpect(status().isCreated()));
    }

    private void receive(String keeper, String product, String lot, LocalDate expires) throws Exception {
        postJson(keeper, S1, "/api/v1/inventory/receipts", Map.of("productId", product, "quantity", 1, "lotNumber", lot,
                "expiresAt", expires.toString())).andExpect(status().isCreated());
    }

    private String onHand(String keeper, String product) throws Exception {
        for (JsonNode item : body(getJson(keeper, S1, "/api/v1/inventory/stock").andExpect(status().isOk()))) {
            if (item.get("productId").asText().equals(product)) {
                return item.get("onHand").decimalValue().stripTrailingZeros().toPlainString();
            }
        }
        return "0";
    }

    private String speciesId(String code) {
        return text(superUserDataSource(), "SELECT id::text FROM vet.species WHERE code = ?", code);
    }

    private org.springframework.test.web.servlet.ResultActions putJson(String token, String url, Object body) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(url)
                .header("Authorization", "Bearer " + token).header("X-Clinic-Id", S1)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
    }

    private org.springframework.test.web.servlet.ResultActions deleteWith(String token, String url) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(url)
                .header("Authorization", "Bearer " + token).header("X-Clinic-Id", S1));
    }

    private List<String> ids(String token, String url) throws Exception {
        JsonNode page = body(getJson(token, url).andExpect(status().isOk()));
        return StreamSupport.stream(page.get("content").spliterator(), false).map(r -> r.get("recordId").asText()).toList();
    }

    private static JsonNode find(JsonNode page, String recordId) {
        return StreamSupport.stream(page.get("content").spliterator(), false)
                .filter(r -> r.get("recordId").asText().equals(recordId)).findFirst()
                .orElseThrow(() -> new AssertionError("kayıt listede yok: " + recordId));
    }
}
