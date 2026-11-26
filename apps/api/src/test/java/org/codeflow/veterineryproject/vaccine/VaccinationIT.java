package org.codeflow.veterineryproject.vaccine;

import com.fasterxml.jackson.databind.JsonNode;
import org.codeflow.veterineryproject.common.test.ApiTestSupport;
import org.codeflow.veterineryproject.common.test.RegistrationTestData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.codeflow.veterineryproject.common.test.ClinicalTestData.*;
import static org.codeflow.veterineryproject.common.test.RegistrationTestData.*;
import static org.codeflow.veterineryproject.common.test.TenantTestData.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class VaccinationIT extends ApiTestSupport {

    private static final String S1 = BRANCH_A1_S1.toString();
    private static final String DOG_ANIMAL = PATIENT_A1_S1.toString();
    private static final String CAT_ANIMAL = PATIENT_A1_S2.toString();

    @BeforeAll
    static void seed() {
        RegistrationTestData.seed(superUserDataSource());
        update(superUserDataSource(), "UPDATE vet.animals SET species_id = (SELECT id FROM vet.species WHERE code = 'DOG'), birth_date = current_date - 1000 WHERE id = ?", PATIENT_A1_S1);
        update(superUserDataSource(), "UPDATE vet.animals SET species_id = (SELECT id FROM vet.species WHERE code = 'CAT'), birth_date = current_date - 30 WHERE id = ?", PATIENT_A1_S2);
        text(superUserDataSource(), "SELECT vet.seed_default_vaccine_protocols(?)::text", ORG_A);
    }

    // ---- Protokol / şablon ---------------------------------------------------------------------------------------

    @Test
    void seed_defaultProtocolsAreDraftUntilVetApproves() throws Exception {
        String vet = token(ORG_A_CODE, EMAIL_VET_A1);
        JsonNode list = body(getJson(vet, "/api/v1/vaccine-protocols?size=100").andExpect(status().isOk()));

        assertThat(codes(list)).contains("DOG-CORE", "DOG-RABIES", "CAT-CORE", "CAT-RABIES");
        assertThat(protocol(list, "CAT-RABIES").get("status").asText()).isIn("DRAFT", "APPROVED");
    }

    @Test
    void protocol_managementRequiresPermissionAndApprovalNeedsVetProfile() throws Exception {
        String reception = token(ORG_A_CODE, EMAIL_RECEPTION_A1);
        String noProfile = token(ORG_A_CODE, EMAIL_VET_NOPROFILE_A1);
        String vet = token(ORG_A_CODE, EMAIL_VET_A1);
        String draftId = protocol(body(getJson(vet, "/api/v1/vaccine-protocols?size=100")), "CAT-RABIES").get("id").asText();
        Map<String, Object> create = Map.of("code", "CUSTOM-" + UUID.randomUUID().toString().substring(0, 6), "name", "Özel",
                "speciesId", speciesId("DOG"), "doses", java.util.List.of(Map.of("doseNumber", 1, "vaccineName", "X")));

        postJson(reception, S1, "/api/v1/vaccine-protocols", create).andExpect(status().isForbidden());
        postJson(reception, S1, "/api/v1/vaccine-protocols/" + draftId + "/approve", Map.of()).andExpect(status().isForbidden());
        JsonNode created = body(postJson(vet, S1, "/api/v1/vaccine-protocols", create).andExpect(status().isCreated()));
        assertThat(created.get("status").asText()).isEqualTo("DRAFT");
        postJson(noProfile, S1, "/api/v1/vaccine-protocols/" + created.get("id").asText() + "/approve", Map.of())
                .andExpect(status().isUnprocessableEntity());
        postJson(vet, S1, "/api/v1/vaccine-protocols", Map.of("code", "BAD", "name", "x", "speciesId", speciesId("DOG"),
                "doses", java.util.List.of(Map.of("doseNumber", 2, "vaccineName", "X")))).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void template_isGeneratedPerSpeciesFromApprovedProtocols() throws Exception {
        String vet = token(ORG_A_CODE, EMAIL_VET_A1);
        approved(vet, "DOG-CORE");
        approved(vet, "CAT-CORE");

        JsonNode dog = body(getJson(vet, "/api/v1/animals/" + DOG_ANIMAL + "/vaccine-template").andExpect(status().isOk()));
        JsonNode cat = body(getJson(vet, "/api/v1/animals/" + CAT_ANIMAL + "/vaccine-template").andExpect(status().isOk()));

        assertThat(dog.get("speciesCode").asText()).isEqualTo("DOG");
        assertThat(StreamSupport.stream(dog.get("entries").spliterator(), false).map(e -> e.get("protocolCode").asText()))
                .isNotEmpty().allMatch(c -> c.startsWith("DOG-"));
        assertThat(StreamSupport.stream(cat.get("entries").spliterator(), false).map(e -> e.get("protocolCode").asText()))
                .isNotEmpty().allMatch(c -> c.startsWith("CAT-"));
        JsonNode catFirst = cat.get("entries").get(0);
        assertThat(catFirst.get("minimumAgeDays").asInt()).isEqualTo(56);
        assertThat(catFirst.get("eligibleNow").asBoolean()).isFalse();
    }

    // ---- Aşı kaydı -----------------------------------------------------------------------------------------------

    @Test
    void nextDose_isComputedFromTemplateAndBoosterAfterLastDose() throws Exception {
        String vet = token(ORG_A_CODE, EMAIL_VET_A1);
        UUID dose1 = doseId(vet, "DOG-CORE", 1);
        UUID dose3 = doseId(vet, "DOG-CORE", 3);
        String encounter = openEncounter(vet, DOG_ANIMAL);
        OffsetDateTime given = OffsetDateTime.now().minusDays(1);

        JsonNode first = record(vet, encounter, Map.of("protocolDoseId", dose1.toString(), "administeredAt", given.toString(), "batchNumber", "LOT-1"));
        assertThat(first.get("nextDueSource").asText()).isEqualTo("TEMPLATE");
        assertThat(LocalDate.parse(first.get("nextDueAt").asText())).isEqualTo(given.toLocalDate().plusDays(21));
        assertThat(first.get("vaccineName").asText()).isEqualTo("Karma (DHPPi)");
        assertThat(first.get("clinicId").asText()).isEqualTo(S1);
        assertThat(first.get("createdBy").asText()).isEqualTo(VET_A1.toString());

        JsonNode last = record(vet, encounter, Map.of("protocolDoseId", dose3.toString(), "administeredAt", given.toString()));
        assertThat(LocalDate.parse(last.get("nextDueAt").asText())).isEqualTo(given.toLocalDate().plusDays(365));
    }

    @Test
    void nextDose_vetCanOverrideButNotBeforeAdministration() throws Exception {
        String vet = token(ORG_A_CODE, EMAIL_VET_A1);
        UUID dose1 = doseId(vet, "DOG-CORE", 1);
        String encounter = openEncounter(vet, DOG_ANIMAL);
        LocalDate custom = LocalDate.now().plusDays(10);

        JsonNode ok = record(vet, encounter, Map.of("protocolDoseId", dose1.toString(), "nextDueAt", custom.toString()));
        assertThat(ok.get("nextDueSource").asText()).isEqualTo("VET_OVERRIDE");
        assertThat(LocalDate.parse(ok.get("nextDueAt").asText())).isEqualTo(custom);

        postJson(vet, S1, "/api/v1/vaccinations", Map.of("encounterId", encounter, "protocolDoseId", dose1.toString(),
                "nextDueAt", LocalDate.now().minusDays(2).toString())).andExpect(status().isUnprocessableEntity());
        JsonNode free = record(vet, encounter, Map.of("vaccineName", "Serbest aşı"));
        assertThat(free.get("nextDueSource").asText()).isEqualTo("NONE");
    }

    @Test
    void record_draftOrWrongSpeciesProtocolIsRejected() throws Exception {
        String vet = token(ORG_A_CODE, EMAIL_VET_A1);
        approved(vet, "CAT-CORE");
        UUID catDose = doseId(vet, "CAT-CORE", 1);
        UUID draftDose = doseId(vet, "DOG-RABIES", 1);
        retireIfApproved(vet, "DOG-RABIES");
        String dogEncounter = openEncounter(vet, DOG_ANIMAL);

        postJson(vet, S1, "/api/v1/vaccinations", Map.of("encounterId", dogEncounter, "protocolDoseId", catDose.toString()))
                .andExpect(status().isUnprocessableEntity());
        postJson(vet, S1, "/api/v1/vaccinations", Map.of("encounterId", dogEncounter, "protocolDoseId", draftDose.toString()))
                .andExpect(status().isUnprocessableEntity());
        postJson(vet, S1, "/api/v1/vaccinations", Map.of("encounterId", dogEncounter)).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void record_receptionCannotWriteAndVetWithoutProfileGets422() throws Exception {
        String vet = token(ORG_A_CODE, EMAIL_VET_A1);
        String encounter = openEncounter(vet, DOG_ANIMAL);

        postJson(token(ORG_A_CODE, EMAIL_RECEPTION_A1), S1, "/api/v1/vaccinations", Map.of("encounterId", encounter, "vaccineName", "X"))
                .andExpect(status().isForbidden());
        postJson(token(ORG_A_CODE, EMAIL_VET_NOPROFILE_A1), S1, "/api/v1/vaccinations", Map.of("encounterId", encounter, "vaccineName", "X"))
                .andExpect(status().isUnprocessableEntity());
        postJson(vet, "/api/v1/vaccinations", Map.of("encounterId", encounter, "vaccineName", "X")).andExpect(status().isBadRequest());
    }

    @Test
    void crossClinic_encounterOfAnotherGroupOrOrgIsRejectedAndRecordsAreHidden() throws Exception {
        String vet = token(ORG_A_CODE, EMAIL_VET_A1);
        String vetA2 = token(ORG_A_CODE, EMAIL_VET_A2);
        String vetB = token(ORG_B_CODE, EMAIL_VET_B1);
        String foreign = body(postJson(vetA2, BRANCH_A2_S1.toString(), "/api/v1/encounters",
                Map.of("animalId", PATIENT_A2_S1.toString(), "chiefComplaint", "x")).andExpect(status().isCreated())).get("id").asText();
        String own = openEncounter(vet, DOG_ANIMAL);
        String recordId = record(vet, own, Map.of("vaccineName", "Gizli aşı", "nextDueAt", LocalDate.now().plusDays(3).toString())).get("id").asText();

        postJson(vet, S1, "/api/v1/vaccinations", Map.of("encounterId", foreign, "vaccineName", "X")).andExpect(status().isUnprocessableEntity());
        postJson(vetB, BRANCH_B1_S1.toString(), "/api/v1/vaccinations", Map.of("encounterId", own, "vaccineName", "X")).andExpect(status().isUnprocessableEntity());
        getJson(vetA2, "/api/v1/animals/" + DOG_ANIMAL + "/vaccinations").andExpect(status().isNotFound());
        getJson(vetB, "/api/v1/animals/" + DOG_ANIMAL + "/vaccinations/due").andExpect(status().isNotFound());
        assertThat(dueIds(vetA2, "/api/v1/vaccinations/due?status=ALL&days=365&size=100")).doesNotContain(recordId);
        assertThat(dueIds(vetB, "/api/v1/vaccinations/due?status=ALL&days=365&size=100")).doesNotContain(recordId);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/vaccinations/" + recordId).header("Authorization", "Bearer " + vetA2)
                .header("X-Clinic-Id", BRANCH_A2_S1.toString())).andExpect(status().isNotFound());
    }

    @Test
    void record_closedEncounterRejectsNewVaccine() throws Exception {
        String vet = token(ORG_A_CODE, EMAIL_VET_A1);
        String closed = closedEncounter(vet, DOG_ANIMAL);

        postJson(vet, S1, "/api/v1/vaccinations", Map.of("encounterId", closed, "vaccineName", "X")).andExpect(status().isConflict());
    }

    @Test
    void record_otherBranchOfSameGroupCannotAddToEncounterButSeesRecords() throws Exception {
        String vet = token(ORG_A_CODE, EMAIL_VET_A1);
        String sibling = token(ORG_A_CODE, EMAIL_VET_A1_S2);
        String encounter = openEncounter(vet, DOG_ANIMAL);
        String recordId = record(vet, encounter, Map.of("vaccineName", "Kardeş aşı")).get("id").asText();

        postJson(sibling, BRANCH_A1_S2.toString(), "/api/v1/vaccinations", Map.of("encounterId", encounter, "vaccineName", "X")).andExpect(status().isForbidden());
        JsonNode records = body(getJson(sibling, "/api/v1/animals/" + DOG_ANIMAL + "/vaccinations").andExpect(status().isOk()));
        assertThat(StreamSupport.stream(records.get("content").spliterator(), false).map(r -> r.get("id").asText())).contains(recordId);
    }

    // ---- Yaklaşan / geciken ---------------------------------------------------------------------------------------

    @Test
    void due_overdueAndUpcomingAreListedAndLatestRecordSupersedesOlderOne() throws Exception {
        String vet = token(ORG_A_CODE, EMAIL_VET_A1);
        String sibling = token(ORG_A_CODE, EMAIL_VET_A1_S2);
        String encounter = openEncounter(vet, DOG_ANIMAL);
        String name = "Vade aşısı " + UUID.randomUUID().toString().substring(0, 6);
        String overdue = record(vet, encounter, Map.of("vaccineName", name, "administeredAt", OffsetDateTime.now().minusDays(60).toString(),
                "nextDueAt", LocalDate.now().minusDays(5).toString())).get("id").asText();

        JsonNode overdueList = body(getJson(vet, "/api/v1/animals/" + DOG_ANIMAL + "/vaccinations/due?status=OVERDUE").andExpect(status().isOk()));
        JsonNode row = find(overdueList, overdue);
        assertThat(row).isNotNull();
        assertThat(row.get("status").asText()).isEqualTo("OVERDUE");
        assertThat(row.get("daysOverdue").asLong()).isEqualTo(5);
        assertThat(dueIds(sibling, "/api/v1/vaccinations/due?status=OVERDUE&size=100")).contains(overdue);

        String upcoming = record(vet, encounter, Map.of("vaccineName", name, "nextDueAt", LocalDate.now().plusDays(7).toString())).get("id").asText();
        assertThat(dueIds(vet, "/api/v1/vaccinations/due?status=OVERDUE&size=100")).doesNotContain(overdue, upcoming);
        assertThat(dueIds(vet, "/api/v1/vaccinations/due?status=UPCOMING&days=30&size=100")).contains(upcoming).doesNotContain(overdue);
        assertThat(dueIds(vet, "/api/v1/vaccinations/due?status=UPCOMING&days=3&size=100")).doesNotContain(upcoming);
        getJson(vet, "/api/v1/vaccinations/due?status=BOGUS").andExpect(status().isUnprocessableEntity());
    }

    // ---- Silme, audit, geçmiş ---------------------------------------------------------------------------------------

    @Test
    void delete_requiresPermissionAndWritesAuditWithoutMedicalContent() throws Exception {
        String vet = token(ORG_A_CODE, EMAIL_VET_A1);
        String encounter = openEncounter(vet, DOG_ANIMAL);
        String secret = "GIZLI-ASI-NOTU-5521";
        JsonNode created = record(vet, encounter, Map.of("vaccineName", "Silinecek aşı", "notes", secret, "batchNumber", "LOT-X"));
        String id = created.get("id").asText();

        assertThat(count(superUserDataSource(), "SELECT count(*) FROM vet.audit_logs WHERE action = 'VACCINE_RECORD_CREATE' AND entity_id = ?", UUID.fromString(id))).isEqualTo(1);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/vaccinations/" + id).header("Authorization", "Bearer " + token(ORG_A_CODE, EMAIL_VET_NODELETE_A1))
                .header("X-Clinic-Id", S1)).andExpect(status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/vaccinations/" + id).header("Authorization", "Bearer " + token(ORG_A_CODE, EMAIL_RECEPTION_A1))
                .header("X-Clinic-Id", S1)).andExpect(status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/vaccinations/" + id).header("Authorization", "Bearer " + vet).header("X-Clinic-Id", S1))
                .andExpect(status().isNoContent());

        assertThat(count(superUserDataSource(), "SELECT count(*) FROM vet.audit_logs WHERE action = 'VACCINE_RECORD_DELETE' AND entity_id = ?", UUID.fromString(id))).isEqualTo(1);
        assertThat(count(superUserDataSource(), "SELECT count(*) FROM vet.audit_logs a WHERE a::text LIKE ?", "%" + secret + "%")).isZero();
        assertThat(count(superUserDataSource(), "SELECT count(*) FROM vet.vaccine_records WHERE id = ?", UUID.fromString(id))).isZero();
    }

    @Test
    void delete_vaccineOfClosedEncounterReturns409() throws Exception {
        String vet = token(ORG_A_CODE, EMAIL_VET_A1);
        String encounter = openEncounter(vet, DOG_ANIMAL);
        String id = record(vet, encounter, Map.of("vaccineName", "Kapanacak aşı")).get("id").asText();
        finish(vet, encounter);

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/vaccinations/" + id).header("Authorization", "Bearer " + vet).header("X-Clinic-Id", S1))
                .andExpect(status().isConflict());
    }

    @Test
    void history_isChronologicalPagedAndCombinesEncounterVaccineAndTreatment() throws Exception {
        String vet = token(ORG_A_CODE, EMAIL_VET_A1);
        String encounter = openEncounter(vet, CAT_ANIMAL);
        record(vet, encounter, Map.of("vaccineName", "Geçmiş aşı"));
        postJson(vet, S1, "/api/v1/encounters/" + encounter + "/treatments", Map.of("itemType", "PROCEDURE", "name", "Pansuman"))
                .andExpect(status().isCreated());

        JsonNode all = body(getJson(vet, "/api/v1/animals/" + CAT_ANIMAL + "/history?size=50").andExpect(status().isOk()));
        assertThat(StreamSupport.stream(all.get("content").spliterator(), false).map(i -> i.get("type").asText()))
                .contains("ENCOUNTER", "VACCINATION", "TREATMENT");
        OffsetDateTime previous = null;
        for (JsonNode item : all.get("content")) {
            OffsetDateTime at = org.codeflow.veterineryproject.common.time.ApiDateTime.parseOffset(item.get("occurredAt").asText());
            if (previous != null) {
                assertThat(at).isBeforeOrEqualTo(previous);
            }
            previous = at;
        }
        JsonNode firstPage = body(getJson(vet, "/api/v1/animals/" + CAT_ANIMAL + "/history?size=1&page=0").andExpect(status().isOk()));
        assertThat(firstPage.get("content")).hasSize(1);
        assertThat(firstPage.get("totalElements").asLong()).isGreaterThanOrEqualTo(3);
    }

    // ---- Yardımcılar ----------------------------------------------------------------------------------------------

    private JsonNode record(String token, String encounterId, Map<String, Object> fields) throws Exception {
        java.util.Map<String, Object> request = new java.util.HashMap<>(fields);
        request.put("encounterId", encounterId);
        return body(postJson(token, S1, "/api/v1/vaccinations", request).andExpect(status().isCreated()));
    }

    private String openEncounter(String token, String animalId) throws Exception {
        return body(postJson(token, S1, "/api/v1/encounters", Map.of("animalId", animalId, "chiefComplaint", "Aşı için geldi"))
                .andExpect(status().isCreated())).get("id").asText();
    }

    private String closedEncounter(String token, String animalId) throws Exception {
        String id = openEncounter(token, animalId);
        finish(token, id);
        return id;
    }

    private void finish(String token, String id) throws Exception {
        postJson(token, S1, "/api/v1/encounters/" + id + "/examination", Map.of("examinationSummary", "Normal")).andExpect(status().isOk());
        postJson(token, S1, "/api/v1/encounters/" + id + "/diagnosis", Map.of("assessment", "Sağlıklı")).andExpect(status().isOk());
        postJson(token, S1, "/api/v1/encounters/" + id + "/plan", Map.of("treatmentPlan", "Yok")).andExpect(status().isOk());
        postJson(token, S1, "/api/v1/encounters/" + id + "/close", Map.of()).andExpect(status().isOk());
    }

    private void approved(String vet, String code) throws Exception {
        JsonNode p = protocol(body(getJson(vet, "/api/v1/vaccine-protocols?size=100")), code);
        if ("DRAFT".equals(p.get("status").asText())) {
            postJson(vet, S1, "/api/v1/vaccine-protocols/" + p.get("id").asText() + "/approve", Map.of()).andExpect(status().isOk());
        }
    }

    private void retireIfApproved(String vet, String code) throws Exception {
        JsonNode p = protocol(body(getJson(vet, "/api/v1/vaccine-protocols?size=100")), code);
        if ("APPROVED".equals(p.get("status").asText())) {
            postJson(vet, S1, "/api/v1/vaccine-protocols/" + p.get("id").asText() + "/retire", Map.of()).andExpect(status().isOk());
        }
    }

    /** Protokol onaylanmamışsa onaylar, böylece doz id'si ilgili testte kullanılabilir. */
    private UUID doseId(String vet, String code, int number) throws Exception {
        if (!"DOG-RABIES".equals(code)) {
            approved(vet, code);
        }
        JsonNode p = protocol(body(getJson(vet, "/api/v1/vaccine-protocols?size=100")), code);
        for (JsonNode d : p.get("doses")) {
            if (d.get("doseNumber").asInt() == number) {
                return UUID.fromString(d.get("id").asText());
            }
        }
        throw new AssertionError("dose not found");
    }

    private JsonNode protocol(JsonNode page, String code) {
        for (JsonNode p : page.get("content")) {
            if (code.equals(p.get("code").asText()) && !"RETIRED".equals(p.get("status").asText())) {
                return p;
            }
        }
        throw new AssertionError("protocol not found: " + code);
    }

    private java.util.List<String> codes(JsonNode page) {
        return StreamSupport.stream(page.get("content").spliterator(), false).map(p -> p.get("code").asText()).toList();
    }

    private String speciesId(String code) {
        return text(superUserDataSource(), "SELECT id::text FROM vet.species WHERE code = ?", code);
    }

    private java.util.List<String> dueIds(String token, String url) throws Exception {
        JsonNode page = body(getJson(token, url).andExpect(status().isOk()));
        return StreamSupport.stream(page.get("content").spliterator(), false).map(r -> r.get("recordId").asText()).toList();
    }

    private JsonNode find(JsonNode page, String recordId) {
        for (JsonNode r : page.get("content")) {
            if (recordId.equals(r.get("recordId").asText())) {
                return r;
            }
        }
        return null;
    }
}
