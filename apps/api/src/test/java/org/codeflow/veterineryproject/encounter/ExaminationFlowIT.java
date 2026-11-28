package org.codeflow.veterineryproject.encounter;

import com.fasterxml.jackson.databind.JsonNode;
import org.codeflow.veterineryproject.common.test.ApiTestSupport;
import org.codeflow.veterineryproject.common.test.ClinicalFlowTestData;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.ResultActions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.codeflow.veterineryproject.common.test.ClinicalFlowTestData.*;
import static org.codeflow.veterineryproject.common.test.ClinicalTestData.appointmentStatus;
import static org.codeflow.veterineryproject.common.test.ClinicalTestData.count;
import static org.codeflow.veterineryproject.common.test.InventoryTestData.EMAIL_KEEPER_A1;
import static org.codeflow.veterineryproject.common.test.RegistrationTestData.*;
import static org.codeflow.veterineryproject.common.test.TenantTestData.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** FG-C1..C4: muayene türlerine göre akış (Template Method), reçete, kapatma; yetki ve kiracı izolasyonu. */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Muayene türleri ve reçete")
class ExaminationFlowIT extends ApiTestSupport {

    private static final String S1 = BRANCH_A1_S1.toString();
    private static final String S2 = BRANCH_A1_S2.toString();
    private static final String ENC = "/api/v1/encounters";

    @BeforeAll
    static void seed() {
        ClinicalFlowTestData.seed(superUserDataSource());
    }

    @AfterAll
    static void cleanup() {
        ClinicalFlowTestData.cleanup(superUserDataSource());
    }

    // ---- FG-C1 / FG-C2: akış türleri ------------------------------------------------------------------------------

    @Test
    @DisplayName("akış tanımları dört türü ve türe göre adımları döner")
    void flows_describeFourTypesWithTheirSteps() throws Exception {
        JsonNode flows = body(getJson(token(ORG_A_CODE, EMAIL_RECEPTION_A1), "/api/v1/encounter-flows").andExpect(status().isOk()));

        assertThat(codes(flows)).containsExactly("GENERAL", "VACCINATION", "EMERGENCY", "SURGERY");
        assertThat(steps(flow(flows, "VACCINATION"))).containsExactly("ADMISSION", "EXAMINATION", "PLAN");
        assertThat(steps(flow(flows, "GENERAL"))).containsExactly("ADMISSION", "EXAMINATION", "DIAGNOSIS", "PLAN");
        assertThat(texts(flow(flows, "EMERGENCY").get("steps").get(0).get("required"))).containsExactly("chiefComplaint", "temperatureC");
        assertThat(texts(flow(flows, "SURGERY").get("steps").get(1).get("required"))).contains("weightKg");
    }

    @Test
    @DisplayName("aşı ziyareti tanı adımını atlar; incelemede kilo ve ısı zorunlu, ateşte uyarı verir")
    void vaccinationVisit_skipsDiagnosisAndRequiresVitals() throws Exception {
        String vet = token(ORG_A_CODE, EMAIL_VET_A1);
        JsonNode e = start(vet, DOG, "VACCINATION", Map.of());
        assertThat(e.get("status").asText()).isEqualTo("ADMISSION");
        String id = e.get("id").asText();

        postJson(vet, S1, ENC + "/" + id + "/examination", Map.of("examinationSummary", "Genel durum iyi"))
                .andExpect(status().isUnprocessableEntity());
        JsonNode examined = body(postJson(vet, S1, ENC + "/" + id + "/examination",
                Map.of("weightKg", 12.4, "temperatureC", 39.8)).andExpect(status().isOk()));
        assertThat(examined.get("status").asText()).isEqualTo("EXAMINATION");
        assertThat(examined.get("weightKg").decimalValue()).isEqualByComparingTo("12.4");
        assertThat(texts(examined.get("warnings"))).anyMatch(w -> w.contains("Ateşli"));

        postJson(vet, S1, ENC + "/" + id + "/diagnosis", Map.of("assessment", "x")).andExpect(status().isConflict());
        assertThat(step(vet, id, "plan", Map.of("treatmentPlan", "Karma aşı 1. doz"))).isEqualTo("PLAN");
        assertThat(body(postJson(vet, S1, ENC + "/" + id + "/close", Map.of()).andExpect(status().isOk()))
                .get("status").asText()).isEqualTo("CLOSED");
    }

    @Test
    @DisplayName("acil muayene kabulde vücut ısısı ister ve kritik ısıda uyarır; planda uygulanan işlem zorunlu")
    void emergency_requiresTemperatureAndProcedure() throws Exception {
        String vet = token(ORG_A_CODE, EMAIL_VET_A1);
        postJson(vet, S1, ENC, Map.of("animalId", DOG.toString(), "encounterType", "EMERGENCY", "chiefComplaint", "Zehirlenme"))
                .andExpect(status().isUnprocessableEntity());
        JsonNode e = start(vet, DOG, "EMERGENCY", Map.of("chiefComplaint", "Zehirlenme", "temperatureC", 41.2));
        assertThat(texts(e.get("warnings"))).anyMatch(w -> w.contains("Kritik vücut ısısı"));
        String id = e.get("id").asText();
        step(vet, id, "examination", Map.of("examinationSummary", "Hipersalivasyon"));
        step(vet, id, "diagnosis", Map.of("assessment", "Organofosfat zehirlenmesi"));

        postJson(vet, S1, ENC + "/" + id + "/plan", Map.of("treatmentPlan", "Atropin")).andExpect(status().isUnprocessableEntity());
        postJson(vet, S1, ENC + "/" + id + "/treatments", Map.of("itemType", "PROCEDURE", "name", "Mide lavajı"))
                .andExpect(status().isCreated());
        assertThat(step(vet, id, "plan", Map.of("treatmentPlan", "Atropin"))).isEqualTo("PLAN");
    }

    @Test
    @DisplayName("cerrahi anamnez ve kilo ister; operasyon notu yoksa kapatmada uyarı verir")
    void surgery_requiresHistoryAndWeightAndWarnsWithoutOperationNote() throws Exception {
        String vet = token(ORG_A_CODE, EMAIL_VET_A1);
        postJson(vet, S1, ENC, Map.of("animalId", CAT.toString(), "encounterType", "SURGERY", "chiefComplaint", "Kısırlaştırma"))
                .andExpect(status().isUnprocessableEntity());
        String id = start(vet, CAT, "SURGERY", Map.of("chiefComplaint", "Kısırlaştırma", "history", "Aşıları tam"))
                .get("id").asText();
        postJson(vet, S1, ENC + "/" + id + "/examination", Map.of("examinationSummary", "Normal")).andExpect(status().isUnprocessableEntity());
        step(vet, id, "examination", Map.of("examinationSummary", "Normal", "weightKg", 3.6));
        step(vet, id, "diagnosis", Map.of("assessment", "Sağlıklı"));
        postJson(vet, S1, ENC + "/" + id + "/treatments", Map.of("itemType", "PROCEDURE", "name", "Ovariohisterektomi"))
                .andExpect(status().isCreated());
        step(vet, id, "plan", Map.of("treatmentPlan", "Antibiyotik 5 gün"));

        JsonNode closed = body(postJson(vet, S1, ENC + "/" + id + "/close", Map.of()).andExpect(status().isOk()));
        assertThat(closed.get("status").asText()).isEqualTo("CLOSED");
        assertThat(texts(closed.get("warnings"))).anyMatch(w -> w.contains("Operasyon notu"));
    }

    @Test
    @DisplayName("randevudan aşı ziyareti başlar; kapatınca randevu COMPLETED olur ve kapalı muayene düzenlenemez")
    void close_vaccinationVisitFromAppointmentCompletesAppointmentAndLocksRecord() throws Exception {
        String vet = token(ORG_A_CODE, EMAIL_VET_A1);
        UUID appointment = UUID.randomUUID();
        org.codeflow.veterineryproject.common.test.ClinicalTestData.update(superUserDataSource(),
                "INSERT INTO vet.appointments (id, organization_id, clinic_id, client_id, animal_id, appointment_no, status, starts_at, ends_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, 'CHECKED_IN', now() + interval '40 days', now() + interval '40 days 1 hour')",
                appointment, ORG_A, BRANCH_A1_S1, OWNER, DOG, "IT27-" + appointment.toString().substring(0, 8));
        JsonNode e = body(postJson(vet, S1, "/api/v1/appointments/" + appointment + "/encounter",
                Map.of("encounterType", "VACCINATION", "chiefComplaint", "Aşı")).andExpect(status().isCreated()));
        assertThat(e.get("encounterType").asText()).isEqualTo("VACCINATION");
        String id = e.get("id").asText();
        step(vet, id, "examination", Map.of("weightKg", 20, "temperatureC", 38.6));
        step(vet, id, "plan", Map.of("treatmentPlan", "Kuduz aşısı"));
        postJson(vet, S1, ENC + "/" + id + "/close", Map.of()).andExpect(status().isOk());

        assertThat(appointmentStatus(superUserDataSource(), appointment)).isEqualTo("COMPLETED");
        postJson(vet, S1, ENC + "/" + id + "/plan", Map.of("treatmentPlan", "x")).andExpect(status().isConflict());
        postJson(vet, S1, ENC + "/" + id + "/examination", Map.of("weightKg", 21)).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("resepsiyon hiçbir türde muayene açamaz (403)")
    void role_receptionCannotStartAnyEncounterType() throws Exception {
        String reception = token(ORG_A_CODE, EMAIL_RECEPTION_A1);
        for (String type : List.of("CONSULTATION", "VACCINATION", "EMERGENCY", "SURGERY")) {
            postJson(reception, S1, ENC, Map.of("animalId", DOG.toString(), "encounterType", type, "chiefComplaint", "x",
                    "temperatureC", 38, "history", "x")).andExpect(status().isForbidden());
        }
    }

    @Test
    @DisplayName("ölçüm aralık dışında ise 400")
    void validation_vitalsOutOfRangeAreBadRequest() throws Exception {
        String vet = token(ORG_A_CODE, EMAIL_VET_A1);
        postJson(vet, S1, ENC, Map.of("animalId", DOG.toString(), "encounterType", "EMERGENCY", "chiefComplaint", "x",
                "temperatureC", 60)).andExpect(status().isBadRequest());
        postJson(vet, S1, ENC, Map.of("animalId", DOG.toString(), "encounterType", "BOGUS", "chiefComplaint", "x"))
                .andExpect(status().isBadRequest());
    }

    // ---- FG-C3: reçete --------------------------------------------------------------------------------------------

    @Test
    @DisplayName("reçete kalemi eklenir, listelenir ve silinir; ilaç olmayan ürün reddedilir")
    void prescription_addListDeleteAndOnlyMedication() throws Exception {
        String vet = token(ORG_A_CODE, EMAIL_VET_A1);
        String drug = product("MEDICATION", "Amoksisilin 250 mg");
        String food = product("FOOD", "Mama");
        String id = start(vet, DOG, "CONSULTATION", Map.of("chiefComplaint", "Öksürük")).get("id").asText();

        JsonNode item = body(postJson(vet, S1, ENC + "/" + id + "/prescriptions", Map.of("productId", drug, "dose", 250,
                "doseUnit", "mg", "frequencyText", "12 saatte bir", "durationDays", 7, "instructions", "Tok karnına"))
                .andExpect(status().isCreated()));
        assertThat(item.get("productName").asText()).startsWith("Amoksisilin 250 mg");
        assertThat(item.get("prescriptionNo").asText()).matches("RX-\\d{6}");
        assertThat(item.get("lineNo").asInt()).isEqualTo(1);
        JsonNode second = body(postJson(vet, S1, ENC + "/" + id + "/prescriptions", Map.of("productId", drug,
                "frequencyText", "Günde 1")).andExpect(status().isCreated()));
        assertThat(second.get("prescriptionId").asText()).isEqualTo(item.get("prescriptionId").asText());
        assertThat(second.get("lineNo").asInt()).isEqualTo(2);

        postJson(vet, S1, ENC + "/" + id + "/prescriptions", Map.of("productId", food, "frequencyText", "x"))
                .andExpect(status().isUnprocessableEntity());
        postJson(vet, S1, ENC + "/" + id + "/prescriptions", Map.of("productId", drug, "dose", 1, "frequencyText", "x"))
                .andExpect(status().isUnprocessableEntity());
        postJson(vet, S1, ENC + "/" + id + "/prescriptions", Map.of("productId", drug)).andExpect(status().isBadRequest());

        assertThat(body(getJson(token(ORG_A_CODE, EMAIL_RECEPTION_A1), ENC + "/" + id + "/prescriptions").andExpect(status().isOk())))
                .hasSize(2);
        deleteWith(vet, S1, ENC + "/" + id + "/prescriptions/" + item.get("id").asText()).andExpect(status().isNoContent());
        deleteWith(vet, S1, ENC + "/" + id + "/prescriptions/" + second.get("id").asText()).andExpect(status().isNoContent());
        assertThat(body(getJson(vet, ENC + "/" + id + "/prescriptions"))).isEmpty();
        assertThat(count(superUserDataSource(), "SELECT count(*) FROM vet.prescriptions WHERE encounter_id = ?", UUID.fromString(id))).isZero();
        deleteWith(vet, S1, ENC + "/" + id).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("kapalı muayenede reçete eklenemez/silinemez (409)")
    void prescription_closedEncounterIsConflict() throws Exception {
        String vet = token(ORG_A_CODE, EMAIL_VET_A1);
        String drug = product("MEDICATION", "Meloksikam");
        String id = start(vet, DOG, "CONSULTATION", Map.of("chiefComplaint", "Topallık")).get("id").asText();
        String item = body(postJson(vet, S1, ENC + "/" + id + "/prescriptions", Map.of("productId", drug, "frequencyText", "Günde 1"))
                .andExpect(status().isCreated())).get("id").asText();
        step(vet, id, "examination", Map.of("examinationSummary", "Sol ön bacak hassas"));
        step(vet, id, "diagnosis", Map.of("assessment", "Burkulma"));
        step(vet, id, "plan", Map.of("treatmentPlan", "İstirahat"));
        postJson(vet, S1, ENC + "/" + id + "/close", Map.of()).andExpect(status().isOk());

        postJson(vet, S1, ENC + "/" + id + "/prescriptions", Map.of("productId", drug, "frequencyText", "x")).andExpect(status().isConflict());
        deleteWith(vet, S1, ENC + "/" + id + "/prescriptions/" + item).andExpect(status().isConflict());
        assertThat(body(getJson(vet, ENC + "/" + id + "/prescriptions"))).hasSize(1);
    }

    @Test
    @DisplayName("reçete: resepsiyon 403, kardeş şube 403, başka organizasyon 404")
    void prescription_roleAndTenantIsolation() throws Exception {
        String vet = token(ORG_A_CODE, EMAIL_VET_A1);
        String drug = product("MEDICATION", "Prednizolon");
        String id = start(vet, DOG, "CONSULTATION", Map.of("chiefComplaint", "Kaşıntı")).get("id").asText();
        String item = body(postJson(vet, S1, ENC + "/" + id + "/prescriptions", Map.of("productId", drug, "frequencyText", "Günde 1"))
                .andExpect(status().isCreated())).get("id").asText();
        Map<String, Object> request = Map.of("productId", drug, "frequencyText", "x");

        postJson(token(ORG_A_CODE, EMAIL_RECEPTION_A1), S1, ENC + "/" + id + "/prescriptions", request).andExpect(status().isForbidden());
        postJson(token(ORG_A_CODE, EMAIL_VET_A1_S2), S2, ENC + "/" + id + "/prescriptions", request).andExpect(status().isForbidden());
        String vetB = token(ORG_B_CODE, EMAIL_VET_B1);
        getJson(vetB, ENC + "/" + id + "/prescriptions").andExpect(status().isNotFound());
        postJson(vetB, BRANCH_B1_S1.toString(), ENC + "/" + id + "/prescriptions", request).andExpect(status().isNotFound());
        deleteWith(vetB, BRANCH_B1_S1.toString(), ENC + "/" + id + "/prescriptions/" + item).andExpect(status().isNotFound());
        getJson(token(ORG_A_CODE, EMAIL_VET_A2), ENC + "/" + id + "/prescriptions").andExpect(status().isNotFound());
        assertThat(body(getJson(vet, ENC + "/" + id + "/prescriptions"))).hasSize(1);
    }

    @Test
    @DisplayName("eşzamanlı reçete kalemleri tek reçete başlığında, tekil satır numaralarıyla yazılır")
    void prescription_concurrentItemsShareOneHeader() throws Exception {
        String vet = token(ORG_A_CODE, EMAIL_VET_A1);
        String drug = product("MEDICATION", "Sefaleksin");
        String id = start(vet, DOG, "CONSULTATION", Map.of("chiefComplaint", "Yara")).get("id").asText();
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(6);
        try {
            List<java.util.concurrent.Callable<JsonNode>> jobs = new java.util.ArrayList<>();
            for (int i = 0; i < 6; i++) {
                jobs.add(() -> body(postJson(vet, S1, ENC + "/" + id + "/prescriptions", Map.of("productId", drug,
                        "frequencyText", "Günde 2")).andExpect(status().isCreated())));
            }
            java.util.Set<String> headers = new java.util.HashSet<>();
            java.util.Set<Integer> lines = new java.util.HashSet<>();
            for (java.util.concurrent.Future<JsonNode> f : pool.invokeAll(jobs)) {
                headers.add(f.get().get("prescriptionId").asText());
                lines.add(f.get().get("lineNo").asInt());
            }
            assertThat(headers).hasSize(1);
            assertThat(lines).containsExactlyInAnyOrder(1, 2, 3, 4, 5, 6);
        } finally {
            pool.shutdownNow();
        }
    }

    // ---- yardımcılar ----------------------------------------------------------------------------------------------

    private JsonNode start(String token, UUID animal, String type, Map<String, Object> fields) throws Exception {
        Map<String, Object> request = new HashMap<>(fields);
        request.put("animalId", animal.toString());
        request.put("encounterType", type);
        return body(postJson(token, S1, ENC, request).andExpect(status().isCreated()));
    }

    private String step(String token, String id, String name, Object request) throws Exception {
        return body(postJson(token, S1, ENC + "/" + id + "/" + name, request).andExpect(status().isOk())).get("status").asText();
    }

    private String product(String type, String name) throws Exception {
        return body(postJson(token(ORG_A_CODE, EMAIL_KEEPER_A1), S1, "/api/v1/products", Map.of("name", name + " " + UUID.randomUUID()
                        .toString().substring(0, 4), "productType", type, "baseUnit", "adet", "tracksLot", false, "minimumStock", 0,
                "salePrice", "10.00", "taxRate", 10)).andExpect(status().isCreated())).get("id").asText();
    }

    private ResultActions deleteWith(String token, String clinic, String url) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(url)
                .header("Authorization", "Bearer " + token).header("X-Clinic-Id", clinic));
    }

    private static JsonNode flow(JsonNode flows, String code) {
        return StreamSupport.stream(flows.spliterator(), false).filter(f -> f.get("code").asText().equals(code)).findFirst().orElseThrow();
    }

    private static List<String> codes(JsonNode flows) {
        return StreamSupport.stream(flows.spliterator(), false).map(f -> f.get("code").asText()).toList();
    }

    private static List<String> steps(JsonNode flow) {
        return StreamSupport.stream(flow.get("steps").spliterator(), false).map(s -> s.get("status").asText()).toList();
    }

    private static List<String> texts(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false).map(JsonNode::asText).toList();
    }
}
