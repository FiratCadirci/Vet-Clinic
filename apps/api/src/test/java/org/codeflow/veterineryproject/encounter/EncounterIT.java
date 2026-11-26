package org.codeflow.veterineryproject.encounter;

import com.fasterxml.jackson.databind.JsonNode;
import org.codeflow.veterineryproject.common.event.DomainEvents.AppointmentStatusChangedEvent;
import org.codeflow.veterineryproject.common.event.DomainEvents.EncounterClosedEvent;
import org.codeflow.veterineryproject.common.test.ApiTestSupport;
import org.codeflow.veterineryproject.common.test.RegistrationTestData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.codeflow.veterineryproject.common.test.ClinicalTestData.*;
import static org.codeflow.veterineryproject.common.test.RegistrationTestData.*;
import static org.codeflow.veterineryproject.common.test.TenantTestData.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(EncounterIT.CaptureConfig.class)
class EncounterIT extends ApiTestSupport {

    private static final String S1 = BRANCH_A1_S1.toString();
    private static final String S2 = BRANCH_A1_S2.toString();
    private static final String SECRET = "GIZLI-TIBBI-METIN-8841";

    @TestConfiguration
    static class CaptureConfig {
        @Bean
        EventCapture eventCapture() {
            return new EventCapture();
        }
    }

    static class EventCapture {
        final List<Object> events = new CopyOnWriteArrayList<>();

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        void on(Object event) {
            if (event.getClass().getName().contains("common.event")) {
                events.add(event);
            }
        }
    }

    @Autowired
    EventCapture capture;
    @Autowired
    ApplicationEventPublisher publisher;
    @Autowired
    PlatformTransactionManager transactionManager;

    @BeforeAll
    static void seed() {
        RegistrationTestData.seed(superUserDataSource());
    }

    // ---- Muayene akışı ------------------------------------------------------------------------------------------

    @Test
    void flow_validTransitionsReachClosed() throws Exception {
        String vet = token(ORG_A_CODE, EMAIL_VET_A1);
        JsonNode e = walkIn(vet, PATIENT_A1_S1);
        assertThat(e.get("status").asText()).isEqualTo("ADMISSION");
        assertThat(e.get("encounterNo").asText()).matches("E-\\d{6}");
        assertThat(e.get("clinicId").asText()).isEqualTo(S1);
        assertThat(e.get("createdAt").asText()).matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}");
        assertThat(e.get("updatedAt").asText()).matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}");
        assertThat(e.get("createdBy").asText()).isEqualTo(VET_A1.toString());
        String id = e.get("id").asText();

        assertThat(step(vet, id, "examination", Map.of("examinationSummary", "Ateş yok"))).isEqualTo("EXAMINATION");
        assertThat(step(vet, id, "diagnosis", Map.of("assessment", "Burkulma"))).isEqualTo("DIAGNOSIS");
        assertThat(step(vet, id, "plan", Map.of("treatmentPlan", "İstirahat"))).isEqualTo("PLAN");
        JsonNode closed = body(postJson(vet, S1, "/api/v1/encounters/" + id + "/close", Map.of("vetNotes", "Kontrol 1 hafta"))
                .andExpect(status().isOk()));
        assertThat(closed.get("status").asText()).isEqualTo("CLOSED");
        assertThat(closed.get("endedAt").isNull()).isFalse();
    }

    @Test
    void flow_invalidTransitionReturns409() throws Exception {
        String vet = token(ORG_A_CODE, EMAIL_VET_A1);
        String id = walkIn(vet, PATIENT_A1_S1).get("id").asText();

        postJson(vet, S1, "/api/v1/encounters/" + id + "/diagnosis", Map.of("assessment", "x")).andExpect(status().isConflict());
        postJson(vet, S1, "/api/v1/encounters/" + id + "/plan", Map.of("treatmentPlan", "x")).andExpect(status().isConflict());
        postJson(vet, S1, "/api/v1/encounters/" + id + "/close", Map.of()).andExpect(status().isConflict());
        step(vet, id, "examination", Map.of("examinationSummary", "Bulgu"));
        postJson(vet, S1, "/api/v1/encounters/" + id + "/examination", Map.of("examinationSummary", "Tekrar")).andExpect(status().isConflict());
    }

    @Test
    void flow_walkInWithoutComplaintIsRejected() throws Exception {
        String vet = token(ORG_A_CODE, EMAIL_VET_A1);
        postJson(vet, S1, "/api/v1/encounters", Map.of("animalId", PATIENT_A1_S1.toString(), "chiefComplaint", " "))
                .andExpect(status().isUnprocessableEntity());
        postJson(vet, "/api/v1/encounters", Map.of("animalId", PATIENT_A1_S1.toString(), "chiefComplaint", "x"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void closedEncounter_cannotBeModifiedOrDeleted() throws Exception {
        String vet = token(ORG_A_CODE, EMAIL_VET_A1);
        String id = closedEncounter(vet, PATIENT_A1_S1);

        patchJson(vet, "/api/v1/encounters/" + id, Map.of("vetNotes", "değiştir")).andExpect(status().isBadRequest());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/encounters/" + id)
                .header("Authorization", "Bearer " + vet).header("X-Clinic-Id", S1)
                .contentType("application/json").content("{\"vetNotes\":\"değiştir\"}")).andExpect(status().isConflict());
        postJson(vet, S1, "/api/v1/encounters/" + id + "/treatments", treatment("PROCEDURE", "Pansuman")).andExpect(status().isConflict());
        postJson(vet, S1, "/api/v1/encounters/" + id + "/examination", Map.of("examinationSummary", "x")).andExpect(status().isConflict());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/encounters/" + id)
                .header("Authorization", "Bearer " + vet).header("X-Clinic-Id", S1)).andExpect(status().isConflict());

        assertThatThrownBy(() -> update(superUserDataSource(), "UPDATE vet.encounters SET vet_notes = 'x' WHERE id = ?", UUID.fromString(id)))
                .hasMessageContaining("Kapatılmış muayene");
        assertThatThrownBy(() -> update(superUserDataSource(), "DELETE FROM vet.encounters WHERE id = ?", UUID.fromString(id)))
                .hasMessageContaining("Kapatılmış muayene");
    }

    @Test
    void addendum_worksOnClosedEncounterAndKeepsOriginal() throws Exception {
        String vet = token(ORG_A_CODE, EMAIL_VET_A1);
        String id = closedEncounter(vet, PATIENT_A1_S1);

        JsonNode addendum = body(postJson(vet, S1, "/api/v1/encounters/" + id + "/addenda", Map.of("note", "Laboratuvar sonucu normal"))
                .andExpect(status().isCreated()));
        assertThat(addendum.get("clinicId").asText()).isEqualTo(S1);

        JsonNode encounter = body(getJson(vet, "/api/v1/encounters/" + id).andExpect(status().isOk()));
        assertThat(encounter.get("addenda")).hasSize(1);
        assertThat(encounter.get("assessment").asText()).isEqualTo("Burkulma");
        assertThat(encounter.get("status").asText()).isEqualTo("CLOSED");
    }

    // ---- Randevu ------------------------------------------------------------------------------------------------

    @Test
    void appointment_cancelledNoShowAndUnconfirmedCannotStartEncounter() throws Exception {
        String vet = token(ORG_A_CODE, EMAIL_VET_A1);
        for (String status : List.of("CANCELLED", "NO_SHOW", "BOOKED", "COMPLETED")) {
            UUID appointment = appointment(superUserDataSource(), ORG_A, BRANCH_A1_S1, PATIENT_A1_S1, status);
            postJson(vet, S1, "/api/v1/appointments/" + appointment + "/encounter", Map.of("chiefComplaint", "x"))
                    .andExpect(status().isConflict());
            assertThat(appointmentStatus(superUserDataSource(), appointment)).isEqualTo(status);
        }
    }

    @Test
    void appointment_closingEncounterCompletesAppointment() throws Exception {
        String vet = token(ORG_A_CODE, EMAIL_VET_A1);
        UUID appointment = appointment(superUserDataSource(), ORG_A, BRANCH_A1_S1, PATIENT_A1_S1, "CONFIRMED");
        capture.events.clear();

        JsonNode e = body(postJson(vet, S1, "/api/v1/appointments/" + appointment + "/encounter", Map.of("chiefComplaint", "Kontrol"))
                .andExpect(status().isCreated()));
        assertThat(e.get("appointmentId").asText()).isEqualTo(appointment.toString());
        assertThat(appointmentStatus(superUserDataSource(), appointment)).isEqualTo("IN_PROGRESS");

        String id = e.get("id").asText();
        step(vet, id, "examination", Map.of("examinationSummary", "Normal"));
        step(vet, id, "diagnosis", Map.of("assessment", "Sağlıklı"));
        step(vet, id, "plan", Map.of("treatmentPlan", "Yok"));
        postJson(vet, S1, "/api/v1/encounters/" + id + "/close", Map.of()).andExpect(status().isOk());

        assertThat(appointmentStatus(superUserDataSource(), appointment)).isEqualTo("COMPLETED");
        assertThat(count(superUserDataSource(), "SELECT count(*) FROM vet.appointment_status_history WHERE appointment_id = ?", appointment))
                .isEqualTo(2);
        assertThat(capture.events).anyMatch(ev -> ev instanceof EncounterClosedEvent c && c.encounterId().toString().equals(id));
        assertThat(capture.events).anyMatch(ev -> ev instanceof AppointmentStatusChangedEvent a
                && a.appointmentId().equals(appointment) && a.newStatus().equals("COMPLETED"));
    }

    @Test
    void appointment_secondOpenEncounterIsBlocked() throws Exception {
        String vet = token(ORG_A_CODE, EMAIL_VET_A1);
        UUID appointment = appointment(superUserDataSource(), ORG_A, BRANCH_A1_S1, PATIENT_A1_S1, "CHECKED_IN");

        postJson(vet, S1, "/api/v1/appointments/" + appointment + "/encounter", Map.of("chiefComplaint", "İlk")).andExpect(status().isCreated());
        postJson(vet, S1, "/api/v1/appointments/" + appointment + "/encounter", Map.of("chiefComplaint", "İkinci")).andExpect(status().isConflict());

        assertThatThrownBy(() -> update(superUserDataSource(), """
                INSERT INTO vet.encounters (organization_id, clinic_id, animal_id, appointment_id, attending_vet_id, encounter_no, status)
                SELECT ?, ?, ?, ?, attending_vet_id, 'E-DUP-' || ?, 'ADMISSION' FROM vet.encounters WHERE appointment_id = ?
                """, ORG_A, BRANCH_A1_S1, PATIENT_A1_S1, appointment, UUID.randomUUID().toString().substring(0, 8), appointment))
                .hasMessageContaining("per_appointment");
    }

    @Test
    void appointment_deletingOpenEncounterReturnsAppointmentToCheckedIn() throws Exception {
        String vet = token(ORG_A_CODE, EMAIL_VET_A1);
        UUID appointment = appointment(superUserDataSource(), ORG_A, BRANCH_A1_S1, PATIENT_A1_S1, "CONFIRMED");
        String id = body(postJson(vet, S1, "/api/v1/appointments/" + appointment + "/encounter", Map.of("chiefComplaint", "x"))
                .andExpect(status().isCreated())).get("id").asText();

        deleteWith(vet, S1, "/api/v1/encounters/" + id).andExpect(status().isNoContent());

        assertThat(appointmentStatus(superUserDataSource(), appointment)).isEqualTo("CHECKED_IN");
    }

    @Test
    void appointment_fromAnotherClinicGroupOrOrganizationIsNotFound() throws Exception {
        String vet = token(ORG_A_CODE, EMAIL_VET_A1);
        UUID otherGroup = appointment(superUserDataSource(), ORG_A, BRANCH_A2_S1, PATIENT_A2_S1, "CONFIRMED");
        UUID otherOrg = appointment(superUserDataSource(), ORG_B, BRANCH_B1_S1, PATIENT_B1_S1, "CONFIRMED");

        postJson(vet, S1, "/api/v1/appointments/" + otherGroup + "/encounter", Map.of("chiefComplaint", "x")).andExpect(status().isNotFound());
        postJson(vet, S1, "/api/v1/appointments/" + otherOrg + "/encounter", Map.of("chiefComplaint", "x")).andExpect(status().isNotFound());
        assertThat(appointmentStatus(superUserDataSource(), otherGroup)).isEqualTo("CONFIRMED");
    }

    @Test
    void appointment_fromSiblingBranchInSameGroupCanBeStarted() throws Exception {
        String vet = token(ORG_A_CODE, EMAIL_VET_A1);
        UUID sibling = appointment(superUserDataSource(), ORG_A, BRANCH_A1_S2, PATIENT_A1_S2, "CONFIRMED");

        JsonNode e = body(postJson(vet, S1, "/api/v1/appointments/" + sibling + "/encounter", Map.of("chiefComplaint", "x"))
                .andExpect(status().isCreated()));

        assertThat(e.get("clinicId").asText()).isEqualTo(S1);
        assertThat(e.get("animalId").asText()).isEqualTo(PATIENT_A1_S2.toString());
    }

    // ---- Roller -------------------------------------------------------------------------------------------------

    @Test
    void role_receptionCanReadButCannotWriteMedicalFields() throws Exception {
        String vet = token(ORG_A_CODE, EMAIL_VET_A1);
        String reception = token(ORG_A_CODE, EMAIL_RECEPTION_A1);
        String id = walkIn(vet, PATIENT_A1_S1).get("id").asText();

        getJson(reception, "/api/v1/encounters/" + id).andExpect(status().isOk());
        getJson(reception, "/api/v1/encounters").andExpect(status().isOk());
        getJson(reception, "/api/v1/animals/" + PATIENT_A1_S1 + "/history").andExpect(status().isOk());

        postJson(reception, S1, "/api/v1/encounters", Map.of("animalId", PATIENT_A1_S1.toString(), "chiefComplaint", "x")).andExpect(status().isForbidden());
        postJson(reception, S1, "/api/v1/encounters/" + id + "/examination", Map.of("examinationSummary", "x")).andExpect(status().isForbidden());
        postJson(reception, S1, "/api/v1/encounters/" + id + "/treatments", treatment("PROCEDURE", "x")).andExpect(status().isForbidden());
        postJson(reception, S1, "/api/v1/encounters/" + id + "/addenda", Map.of("note", "x")).andExpect(status().isForbidden());
        postJson(reception, S1, "/api/v1/vaccinations", Map.of("encounterId", id, "vaccineName", "x")).andExpect(status().isForbidden());
    }

    @Test
    void role_veterinarianWithoutProfileGets422() throws Exception {
        String user = token(ORG_A_CODE, EMAIL_VET_NOPROFILE_A1);

        postJson(user, S1, "/api/v1/encounters", Map.of("animalId", PATIENT_A1_S1.toString(), "chiefComplaint", "x"))
                .andExpect(status().isUnprocessableEntity());
    }

    // ---- Çapraz klinik / kardeş şube ------------------------------------------------------------------------------

    @Test
    void crossClinic_animalFromAnotherClinicGroupOrOrganizationIsRejected() throws Exception {
        String vet = token(ORG_A_CODE, EMAIL_VET_A1);

        postJson(vet, S1, "/api/v1/encounters", Map.of("animalId", PATIENT_A2_S1.toString(), "chiefComplaint", "x"))
                .andExpect(status().isUnprocessableEntity());
        postJson(vet, S1, "/api/v1/encounters", Map.of("animalId", PATIENT_B1_S1.toString(), "chiefComplaint", "x"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void crossClinic_encounterOfAnotherGroupIsNotFoundById() throws Exception {
        String vetA2 = token(ORG_A_CODE, EMAIL_VET_A2);
        String vetA1 = token(ORG_A_CODE, EMAIL_VET_A1);
        String vetB1 = token(ORG_B_CODE, EMAIL_VET_B1);
        String id = body(postJson(vetA2, BRANCH_A2_S1.toString(), "/api/v1/encounters",
                Map.of("animalId", PATIENT_A2_S1.toString(), "chiefComplaint", "x")).andExpect(status().isCreated())).get("id").asText();

        getJson(vetA1, "/api/v1/encounters/" + id).andExpect(status().isNotFound());
        getJson(vetB1, "/api/v1/encounters/" + id).andExpect(status().isNotFound());
        postJson(vetA1, S1, "/api/v1/encounters/" + id + "/examination", Map.of("examinationSummary", "x")).andExpect(status().isNotFound());
        postJson(vetA1, S1, "/api/v1/encounters/" + id + "/addenda", Map.of("note", "x")).andExpect(status().isNotFound());
        deleteWith(vetA1, S1, "/api/v1/encounters/" + id).andExpect(status().isNotFound());
        getJson(vetA1, "/api/v1/animals/" + PATIENT_A2_S1 + "/history").andExpect(status().isNotFound());
        assertThat(body(getJson(vetA1, "/api/v1/encounters?animalId=" + PATIENT_A2_S1)).get("totalElements").asLong()).isZero();
    }

    @Test
    void sibling_authorizedVetSeesOtherBranchEncounterButCannotChangeIt() throws Exception {
        String vet = token(ORG_A_CODE, EMAIL_VET_A1);
        String sibling = token(ORG_A_CODE, EMAIL_VET_A1_S2);
        String id = walkIn(vet, PATIENT_A1_S1).get("id").asText();

        JsonNode seen = body(getJson(sibling, S2, "/api/v1/encounters/" + id).andExpect(status().isOk()));
        assertThat(seen.get("clinicId").asText()).isEqualTo(S1);
        assertThat(body(getJson(sibling, S2, "/api/v1/animals/" + PATIENT_A1_S1 + "/history")).get("content")).isNotEmpty();

        postJson(sibling, S2, "/api/v1/encounters/" + id + "/examination", Map.of("examinationSummary", "x")).andExpect(status().isForbidden());
        postJson(sibling, S2, "/api/v1/encounters/" + id + "/addenda", Map.of("note", "x")).andExpect(status().isForbidden());
        postJson(sibling, S2, "/api/v1/encounters/" + id + "/treatments", treatment("PROCEDURE", "x")).andExpect(status().isForbidden());
        deleteWith(sibling, S2, "/api/v1/encounters/" + id).andExpect(status().isForbidden());
    }

    @Test
    void sibling_employeeWithoutClinicalPermissionCannotSee() throws Exception {
        String vet = token(ORG_A_CODE, EMAIL_VET_A1);
        String id = walkIn(vet, PATIENT_A1_S1).get("id").asText();
        String limited = token(ORG_A_CODE, EMAIL_LIMITED_A1);
        String reader = token(ORG_A_CODE, EMAIL_READER_A1);

        getJson(limited, "/api/v1/encounters/" + id).andExpect(status().isForbidden());
        getJson(reader, "/api/v1/encounters/" + id).andExpect(status().isForbidden());
        getJson(limited, "/api/v1/animals/" + PATIENT_A1_S1 + "/history").andExpect(status().isForbidden());
    }

    // ---- Tedavi ve silme ----------------------------------------------------------------------------------------

    @Test
    void treatment_isAttachedWithNullableProductAndValidated() throws Exception {
        String vet = token(ORG_A_CODE, EMAIL_VET_A1);
        String id = walkIn(vet, PATIENT_A1_S1).get("id").asText();

        JsonNode t = body(postJson(vet, S1, "/api/v1/encounters/" + id + "/treatments", Map.of("itemType", "MEDICATION", "name", "Amoksisilin",
                "dose", 250, "doseUnit", "mg", "frequencyText", "12 saatte bir", "durationDays", 7, "prescribed", true))
                .andExpect(status().isCreated()));
        assertThat(t.get("productId").isNull()).isTrue();
        assertThat(t.get("prescribed").asBoolean()).isTrue();

        postJson(vet, S1, "/api/v1/encounters/" + id + "/treatments", Map.of("itemType", "PROCEDURE", "name", "Pansuman", "prescribed", true))
                .andExpect(status().isUnprocessableEntity());
        postJson(vet, S1, "/api/v1/encounters/" + id + "/treatments", Map.of("itemType", "MEDICATION", "name", "X", "dose", 1))
                .andExpect(status().isUnprocessableEntity());
        postJson(vet, S1, "/api/v1/encounters/" + id + "/treatments", Map.of("itemType", "MEDICATION", "name", "X", "productId", UUID.randomUUID().toString()))
                .andExpect(status().isUnprocessableEntity());
        assertThat(body(getJson(vet, "/api/v1/encounters/" + id + "/treatments"))).hasSize(1);
    }

    @Test
    void delete_encounterWithDependentsReturns409ThenSucceedsWhenEmpty() throws Exception {
        String vet = token(ORG_A_CODE, EMAIL_VET_A1);
        String id = walkIn(vet, PATIENT_A1_S1).get("id").asText();
        String treatment = body(postJson(vet, S1, "/api/v1/encounters/" + id + "/treatments", treatment("PROCEDURE", "Pansuman"))
                .andExpect(status().isCreated())).get("id").asText();

        deleteWith(vet, S1, "/api/v1/encounters/" + id).andExpect(status().isConflict());
        deleteWith(vet, S1, "/api/v1/encounters/" + id + "/treatments/" + treatment).andExpect(status().isNoContent());
        deleteWith(vet, S1, "/api/v1/encounters/" + id).andExpect(status().isNoContent());
        getJson(vet, "/api/v1/encounters/" + id).andExpect(status().isNotFound());

        assertThat(auditCount("ENCOUNTER_DELETE", id)).isEqualTo(1);
        assertThat(auditCount("TREATMENT_DELETE", treatment)).isEqualTo(1);
    }

    @Test
    void delete_withoutDeletePermissionReturns403() throws Exception {
        String vet = token(ORG_A_CODE, EMAIL_VET_A1);
        String noDelete = token(ORG_A_CODE, EMAIL_VET_NODELETE_A1);
        String id = walkIn(vet, PATIENT_A1_S1).get("id").asText();

        deleteWith(noDelete, S1, "/api/v1/encounters/" + id).andExpect(status().isForbidden());
        deleteWith(token(ORG_A_CODE, EMAIL_RECEPTION_A1), S1, "/api/v1/encounters/" + id).andExpect(status().isForbidden());
        getJson(vet, "/api/v1/encounters/" + id).andExpect(status().isOk());
    }

    // ---- Audit ve event'ler ----------------------------------------------------------------------------------------

    @Test
    void audit_recordsCloseWithActorAndNoMedicalContent() throws Exception {
        String vet = token(ORG_A_CODE, EMAIL_VET_A1);
        String id = walkIn(vet, PATIENT_A1_S1, SECRET).get("id").asText();
        step(vet, id, "examination", Map.of("examinationSummary", SECRET));
        step(vet, id, "diagnosis", Map.of("assessment", SECRET));
        step(vet, id, "plan", Map.of("treatmentPlan", SECRET));
        postJson(vet, S1, "/api/v1/encounters/" + id + "/close", Map.of("vetNotes", SECRET)).andExpect(status().isOk());

        assertThat(auditCount("ENCOUNTER_CLOSE", id)).isEqualTo(1);
        assertThat(text(superUserDataSource(), "SELECT actor_user_id::text FROM vet.audit_logs WHERE action = 'ENCOUNTER_CLOSE' AND entity_id = ?",
                UUID.fromString(id))).isEqualTo(VET_A1.toString());
        assertThat(text(superUserDataSource(), "SELECT (occurred_at IS NOT NULL AND clinic_id::text = ? AND old_values IS NULL AND new_values IS NULL AND reason IS NULL)::text "
                + "FROM vet.audit_logs WHERE action = 'ENCOUNTER_CLOSE' AND entity_id = ?", S1, UUID.fromString(id))).isEqualTo("true");
        assertThat(count(superUserDataSource(), "SELECT count(*) FROM vet.audit_logs a WHERE a::text LIKE ?", "%" + SECRET + "%")).isZero();
    }

    @Test
    void events_arePublishedOnlyAfterCommit() {
        capture.events.clear();
        AppointmentStatusChangedEvent event = new AppointmentStatusChangedEvent(ORG_A, UUID.randomUUID(), "A", "B", BRANCH_A1_S1);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        tx.executeWithoutResult(status -> {
            publisher.publishEvent(event);
            assertThat(capture.events).isEmpty();
            status.setRollbackOnly();
        });
        assertThat(capture.events).isEmpty();

        tx.executeWithoutResult(status -> publisher.publishEvent(event));
        assertThat(capture.events).containsExactly(event);
    }

    // ---- Ek dosya -----------------------------------------------------------------------------------------------

    @Test
    void attachment_uploadDownloadAndTenantIsolation() throws Exception {
        String vet = token(ORG_A_CODE, EMAIL_VET_A1);
        String sibling = token(ORG_A_CODE, EMAIL_VET_A1_S2);
        String vetA2 = token(ORG_A_CODE, EMAIL_VET_A2);
        String vetB = token(ORG_B_CODE, EMAIL_VET_B1);
        String id = walkIn(vet, PATIENT_A1_S1).get("id").asText();
        byte[] pdf = "%PDF-1.4 test".getBytes();

        JsonNode meta = body(mvc.perform(multipart("/api/v1/encounters/" + id + "/attachments")
                        .file(new MockMultipartFile("file", "sonuc.pdf", "application/pdf", pdf))
                        .param("documentType", "LAB_REPORT")
                        .header("Authorization", "Bearer " + vet).header("X-Clinic-Id", S1))
                .andExpect(status().isCreated()));
        assertThat(meta.get("mimeType").asText()).isEqualTo("application/pdf");
        assertThat(meta.get("sizeBytes").asLong()).isEqualTo(pdf.length);
        String docId = meta.get("id").asText();

        mvc.perform(get("/api/v1/attachments/" + docId + "/content").header("Authorization", "Bearer " + sibling))
                .andExpect(status().isOk()).andExpect(content().bytes(pdf));
        mvc.perform(get("/api/v1/attachments/" + docId + "/content").header("Authorization", "Bearer " + vetA2)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/attachments/" + docId + "/content").header("Authorization", "Bearer " + vetB)).andExpect(status().isNotFound());
        getJson(vetA2, "/api/v1/encounters/" + id + "/attachments").andExpect(status().isNotFound());
        assertThat(body(getJson(sibling, "/api/v1/encounters/" + id + "/attachments"))).hasSize(1);
        assertThat(count(superUserDataSource(), "SELECT count(*) FROM vet.clinical_documents WHERE object_key LIKE ?", ORG_A + "/encounters/" + id + "/%")).isEqualTo(1);
    }

    @Test
    void attachment_typeSizeAndSignatureAreValidated() throws Exception {
        String vet = token(ORG_A_CODE, EMAIL_VET_A1);
        String id = walkIn(vet, PATIENT_A1_S1).get("id").asText();

        upload(vet, id, new MockMultipartFile("file", "virus.exe", "application/octet-stream", "MZ".getBytes())).andExpect(status().isUnprocessableEntity());
        upload(vet, id, new MockMultipartFile("file", "sahte.pdf", "application/pdf", "bu pdf degil".getBytes())).andExpect(status().isUnprocessableEntity());
        upload(vet, id, new MockMultipartFile("file", "bos.pdf", "application/pdf", new byte[0])).andExpect(status().isUnprocessableEntity());
        byte[] big = new byte[10 * 1024 * 1024 + 1];
        System.arraycopy("%PDF-".getBytes(), 0, big, 0, 5);
        upload(vet, id, new MockMultipartFile("file", "buyuk.pdf", "application/pdf", big)).andExpect(status().isUnprocessableEntity());
        upload(vet, id, new MockMultipartFile("file", "../../etc/passwd.pdf", "application/pdf", "%PDF-1".getBytes())).andExpect(status().isCreated());

        assertThat(count(superUserDataSource(), "SELECT count(*) FROM vet.clinical_documents WHERE encounter_id = ? AND object_key LIKE '%..%'", UUID.fromString(id))).isZero();
    }

    @Test
    void attachment_receptionCannotUploadAndClosedEncounterRejects() throws Exception {
        String vet = token(ORG_A_CODE, EMAIL_VET_A1);
        String reception = token(ORG_A_CODE, EMAIL_RECEPTION_A1);
        String open = walkIn(vet, PATIENT_A1_S1).get("id").asText();
        String closed = closedEncounter(vet, PATIENT_A1_S1);
        MockMultipartFile pdf = new MockMultipartFile("file", "a.pdf", "application/pdf", "%PDF-1.4".getBytes());

        upload(reception, open, pdf).andExpect(status().isForbidden());
        upload(vet, closed, pdf).andExpect(status().isConflict());
    }

    // ---- Yardımcılar ----------------------------------------------------------------------------------------------

    private org.springframework.test.web.servlet.ResultActions upload(String token, String encounterId, MockMultipartFile file) throws Exception {
        return mvc.perform(multipart("/api/v1/encounters/" + encounterId + "/attachments").file(file)
                .header("Authorization", "Bearer " + token).header("X-Clinic-Id", S1));
    }

    private org.springframework.test.web.servlet.ResultActions deleteWith(String token, String clinic, String url) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(url)
                .header("Authorization", "Bearer " + token).header("X-Clinic-Id", clinic));
    }

    private long auditCount(String action, String entityId) {
        return count(superUserDataSource(), "SELECT count(*) FROM vet.audit_logs WHERE action = ? AND entity_id = ?", action, UUID.fromString(entityId));
    }

    private JsonNode walkIn(String token, UUID animal) throws Exception {
        return walkIn(token, animal, "Topallıyor");
    }

    private JsonNode walkIn(String token, UUID animal, String complaint) throws Exception {
        return body(postJson(token, S1, "/api/v1/encounters", Map.of("animalId", animal.toString(), "chiefComplaint", complaint))
                .andExpect(status().isCreated()));
    }

    private String step(String token, String id, String stepName, Object body) throws Exception {
        return body(postJson(token, S1, "/api/v1/encounters/" + id + "/" + stepName, body).andExpect(status().isOk())).get("status").asText();
    }

    private String closedEncounter(String token, UUID animal) throws Exception {
        String id = walkIn(token, animal).get("id").asText();
        step(token, id, "examination", Map.of("examinationSummary", "Ateş yok"));
        step(token, id, "diagnosis", Map.of("assessment", "Burkulma"));
        step(token, id, "plan", Map.of("treatmentPlan", "İstirahat"));
        postJson(token, S1, "/api/v1/encounters/" + id + "/close", Map.of()).andExpect(status().isOk());
        return id;
    }

    private Map<String, Object> treatment(String type, String name) {
        return Map.of("itemType", type, "name", name);
    }

    @SuppressWarnings("unused")
    private void unused() throws SQLException {
    }
}
