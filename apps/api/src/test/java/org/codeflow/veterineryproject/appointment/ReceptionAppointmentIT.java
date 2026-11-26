package org.codeflow.veterineryproject.appointment;

import com.fasterxml.jackson.databind.JsonNode;
import org.codeflow.veterineryproject.common.test.ApiTestSupport;
import org.codeflow.veterineryproject.common.test.RegistrationTestData;
import org.codeflow.veterineryproject.common.test.ClinicalTestData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.codeflow.veterineryproject.common.test.RegistrationTestData.*;
import static org.codeflow.veterineryproject.common.test.TenantTestData.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ReceptionAppointmentIT extends ApiTestSupport {

    private static final String URL = "/api/v1/appointments";
    private static final String CLINIC = BRANCH_A1_S1.toString();

    @BeforeAll
    static void seed() {
        RegistrationTestData.seed(superUserDataSource());
        org.codeflow.veterineryproject.common.test.ClinicalTestData.update(superUserDataSource(),
                "INSERT INTO vet.animal_owners (organization_id, animal_id, client_id) SELECT ?, ?, ? WHERE NOT EXISTS (SELECT 1 FROM vet.animal_owners WHERE animal_id = ? AND client_id = ? AND valid_to IS NULL)",
                ORG_A, PATIENT_A1_S1, PATIENT_A1_S1, PATIENT_A1_S1, PATIENT_A1_S1);
        // Aynı hekim çakışma senaryosunda ikinci hasta da kullanılır; sahiplik kaydı olmadan servis 422 (sahip değil) döner.
        org.codeflow.veterineryproject.common.test.ClinicalTestData.update(superUserDataSource(),
                "INSERT INTO vet.animal_owners (organization_id, animal_id, client_id) SELECT ?, ?, ? WHERE NOT EXISTS (SELECT 1 FROM vet.animal_owners WHERE animal_id = ? AND client_id = ? AND valid_to IS NULL)",
                ORG_A, PATIENT_A1_S2, PATIENT_A1_S2, PATIENT_A1_S2, PATIENT_A1_S2);
    }

    private Map<String, Object> body(OffsetDateTime start) {
        return Map.of("animalId", PATIENT_A1_S1, "clientId", PATIENT_A1_S1, "appointmentType", "EXAM",
                "startsAt", start.toString(), "endsAt", start.plusMinutes(30).toString());
    }

    private JsonNode create(String token, OffsetDateTime start) throws Exception {
        return body(postJson(token, CLINIC, URL, body(start)).andExpect(status().isCreated()));
    }

    private JsonNode create(String token, OffsetDateTime start, UUID animalId, UUID vetId,
                            String appointmentType, String bookingChannel) throws Exception {
        Map<String, Object> request = Map.of("animalId", animalId, "clientId", animalId,
                "veterinarianId", vetId, "appointmentType", appointmentType,
                "startsAt", start.toString(), "endsAt", start.plusMinutes(30).toString(),
                "bookingChannel", bookingChannel);
        return body(postJson(token, CLINIC, URL, request).andExpect(status().isCreated()));
    }

    private String veterinarianId() {
        return ClinicalTestData.text(superUserDataSource(), """
                SELECT v.id FROM vet.veterinarians v
                JOIN vet.staff_members s ON s.organization_id = v.organization_id AND s.id = v.staff_member_id
                WHERE v.organization_id = ? AND s.user_id = ?
                """, ORG_A, VET_A1);
    }

    private String dateRange(OffsetDateTime from, OffsetDateTime to) {
        // MockMvc URL şablonunu kendisi kodlar; önceden kodlamak "%3A"yı "%253A"ya çevirip tarihi bozuyordu.
        return "?startDateTime=" + from + "&endDateTime=" + to;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private JsonNode patch(String token, String path, Object request) throws Exception {
        return body(mvc.perform(MockMvcRequestBuilders.patch(path).header("Authorization", "Bearer " + token)
                        .header("X-Clinic-Id", CLINIC).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk()));
    }

    @Test
    void lifecycleConfirmCheckInThenInvalidTransition() throws Exception {
        String t = token(ORG_A_CODE, EMAIL_FRONTDESK_A1);
        JsonNode a = create(t, OffsetDateTime.now().plusDays(400));
        String id = a.path("id").asText();
        assertThat(a.path("status").asText()).isEqualTo("BOOKED");
        assertThat(a.path("appointmentNo").asText()).startsWith("A-");

        postJson(t, CLINIC, URL + "/" + id + "/confirm", Map.of()).andExpect(status().isOk());
        postJson(t, CLINIC, URL + "/" + id + "/check-in", Map.of()).andExpect(status().isOk());
        postJson(t, CLINIC, URL + "/" + id + "/no-show", Map.of()).andExpect(status().isUnprocessableEntity());

        JsonNode got = body(getJson(t, URL + "/" + id).andExpect(status().isOk()));
        assertThat(got.path("status").asText()).isEqualTo("CHECKED_IN");
    }

    @Test
    void cancelRequiresReasonAndFiltersByStatus() throws Exception {
        String t = token(ORG_A_CODE, EMAIL_FRONTDESK_A1);
        String id = create(t, OffsetDateTime.now().plusDays(410)).path("id").asText();
        postJson(t, CLINIC, URL + "/" + id + "/cancel", Map.of("reason", "")).andExpect(status().isBadRequest());
        postJson(t, CLINIC, URL + "/" + id + "/cancel", Map.of("reason", "Sahibi vazgecti")).andExpect(status().isOk());

        JsonNode list = body(getJson(t, URL + "?status=CANCELLED&clinicId=" + CLINIC).andExpect(status().isOk()))
                .path("content");
        boolean found = false;
        for (JsonNode n : list) {
            assertThat(n.path("status").asText()).isEqualTo("CANCELLED");
            found |= n.path("id").asText().equals(id);
        }
        assertThat(found).isTrue();
    }

    @Test
    void overlappingSlotIsRejectedAndRescheduleWorks() throws Exception {
        String t = token(ORG_A_CODE, EMAIL_FRONTDESK_A1);
        OffsetDateTime start = OffsetDateTime.now().plusDays(420);
        String id = create(t, start).path("id").asText();
        postJson(t, CLINIC, URL + "/" + id + "/reschedule",
                Map.of("startsAt", start.plusDays(1).toString(), "endsAt", start.plusDays(1).plusMinutes(30).toString()))
                .andExpect(status().isOk());
    }

    @Test
    void writeWithoutClinicHeaderIsRejected() throws Exception {
        String t = token(ORG_A_CODE, EMAIL_FRONTDESK_A1);
        postJson(t, URL, body(OffsetDateTime.now().plusDays(430))).andExpect(status().isBadRequest());
    }

    @Test
    void permissionAndAuthChecks() throws Exception {
        postJson(token(ORG_A_CODE, EMAIL_READER_A1), CLINIC, URL, body(OffsetDateTime.now().plusDays(500))).andExpect(status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(URL))
                .andExpect(status().isUnauthorized());
        getJson(token(ORG_A_CODE, EMAIL_FRONTDESK_A1), URL + "/" + UUID.randomUUID()).andExpect(status().isNotFound());
    }

    @Test
    void dailyAndWeeklyCalendarIncludeAppointmentsOverlappingTheWindow() throws Exception {
        String t = token(ORG_A_CODE, EMAIL_FRONTDESK_A1);
        // 23:45-00:15: pencere başlangıcına taşan randevu (23:30-00:00 yarı açık aralıkta pencereyle kesişmez).
        OffsetDateTime startsAt = OffsetDateTime.parse("2032-05-02T23:45:00Z");
        String id = create(t, startsAt).path("id").asText();
        OffsetDateTime monday = OffsetDateTime.parse("2032-05-03T00:00:00Z");

        JsonNode daily = body(getJson(t, URL + dateRange(monday, monday.plusDays(1))).andExpect(status().isOk()));
        JsonNode weekly = body(getJson(t, URL + dateRange(monday, monday.plusDays(7))).andExpect(status().isOk()));
        assertContainsAppointment(daily.path("content"), id);
        assertContainsAppointment(weekly.path("content"), id);
    }

    @Test
    void listSupportsVetTypeChannelOwnerAndMultipleStatuses() throws Exception {
        String t = token(ORG_A_CODE, EMAIL_FRONTDESK_A1);
        UUID vetId = UUID.fromString(veterinarianId());
        OffsetDateTime start = OffsetDateTime.parse("2033-06-01T10:00:00Z");
        JsonNode booked = create(t, start, PATIENT_A1_S1, vetId, "VACCINATION", "WEB");
        JsonNode confirmed = create(t, start.plusHours(1), PATIENT_A1_S1, vetId, "VACCINATION", "WEB");
        postJson(t, CLINIC, URL + "/" + confirmed.path("id").asText() + "/confirm", Map.of())
                .andExpect(status().isOk());

        String filters = "?vetId=" + vetId + "&appointmentType=VACCINATION&bookingChannel=WEB"
                + "&animalId=" + PATIENT_A1_S1 + "&ownerId=" + PATIENT_A1_S1
                + "&status=BOOKED&status=CONFIRMED&sort=startsAt,asc";
        JsonNode result = body(getJson(t, URL + filters).andExpect(status().isOk()));
        assertThat(result.path("content").size()).isGreaterThanOrEqualTo(2);
        assertContainsAppointment(result.path("content"), booked.path("id").asText());
        assertContainsAppointment(result.path("content"), confirmed.path("id").asText());
    }

    @Test
    void overlapConstraintsRejectSameAnimalAndSameVetAppointments() throws Exception {
        String t = token(ORG_A_CODE, EMAIL_FRONTDESK_A1);
        UUID vetId = UUID.fromString(veterinarianId());
        OffsetDateTime animalSlot = OffsetDateTime.parse("2033-07-01T10:00:00Z");
        create(t, animalSlot);
        postJson(t, CLINIC, URL, body(animalSlot.plusMinutes(10))).andExpect(status().isConflict());

        OffsetDateTime vetSlot = OffsetDateTime.parse("2033-07-02T10:00:00Z");
        create(t, vetSlot, PATIENT_A1_S1, vetId, "EXAM", "STAFF");
        Map<String, Object> otherAnimalRequest = Map.of("animalId", PATIENT_A1_S2, "clientId", PATIENT_A1_S2,
                "veterinarianId", vetId, "appointmentType", "EXAM", "startsAt", vetSlot.toString(),
                "endsAt", vetSlot.plusMinutes(30).toString(), "bookingChannel", "STAFF");
        postJson(t, CLINIC, URL, otherAnimalRequest).andExpect(status().isConflict());
    }

    @Test
    void workingHoursAreEnforcedByTheDatabaseTrigger() throws Exception {
        String t = token(ORG_A_CODE, EMAIL_FRONTDESK_A1);
        UUID vetId = UUID.fromString(veterinarianId());
        UUID hoursId = UUID.randomUUID();
        ClinicalTestData.update(superUserDataSource(), """
                INSERT INTO vet.veterinarian_working_hours
                    (id, organization_id, clinic_id, veterinarian_id, day_of_week, starts_at, ends_at, effective_from)
                VALUES (?, ?, ?, ?, 1, '09:00', '17:00', DATE '2034-01-02')
                """, hoursId, ORG_A, BRANCH_A1_S1, vetId);
        try {
            OffsetDateTime outside = OffsetDateTime.parse("2034-01-02T18:00:00+03:00");
            Map<String, Object> request = Map.of("animalId", PATIENT_A1_S1, "clientId", PATIENT_A1_S1,
                    "veterinarianId", vetId, "startsAt", outside.toString(),
                    "endsAt", outside.plusMinutes(30).toString());
            String response = postJson(t, CLINIC, URL, request).andExpect(status().isUnprocessableEntity())
                    .andReturn().getResponse().getContentAsString();
            assertThat(response).contains("VETERINARIAN_OUTSIDE_WORKING_HOURS");
        } finally {
            ClinicalTestData.update(superUserDataSource(), "DELETE FROM vet.veterinarian_working_hours WHERE id = ?", hoursId);
        }
    }

    @Test
    void patchTransitionsAreAuthorizedAndOrganizationIsolated() throws Exception {
        String t = token(ORG_A_CODE, EMAIL_FRONTDESK_A1);
        String id = create(t, OffsetDateTime.now().plusDays(600)).path("id").asText();
        patch(t, URL + "/" + id + "/confirm", Map.of());
        ClinicalTestData.update(superUserDataSource(), """
                INSERT INTO vet.role_permissions (organization_id, role_id, permission_id)
                SELECT ?, ?, id FROM vet.permissions WHERE code = 'appointments.manage'
                ON CONFLICT DO NOTHING
                """, ORG_B, ROLE_MANAGER_B);
        mvc.perform(MockMvcRequestBuilders.get(URL + "/" + id)
                        .header("Authorization", "Bearer " + token(ORG_B_CODE, EMAIL_MANAGER_B1)))
                .andExpect(status().isNotFound());
        getJson(token(ORG_A_CODE, EMAIL_READER_A1), URL + "/" + id).andExpect(status().isForbidden());
    }

    @Test
    void patchCancelRequiresAndPersistsCancellationReason() throws Exception {
        String t = token(ORG_A_CODE, EMAIL_FRONTDESK_A1);
        String id = create(t, OffsetDateTime.now().plusDays(605)).path("id").asText();
        mvc.perform(MockMvcRequestBuilders.patch(URL + "/" + id + "/cancel")
                        .header("Authorization", "Bearer " + t).header("X-Clinic-Id", CLINIC)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("cancellationReason", " "))))
                .andExpect(status().isBadRequest());
        JsonNode cancelled = patch(t, URL + "/" + id + "/cancel",
                Map.of("cancellationReason", "Sahibi randevuyu iptal etti."));
        assertThat(cancelled.path("status").asText()).isEqualTo("CANCELLED");
        assertThat(cancelled.path("cancellationReason").asText()).isEqualTo("Sahibi randevuyu iptal etti.");
    }

    @Test
    void patchLifecycleCreatesAndClosesTheLinkedEncounter() throws Exception {
        String receptionist = token(ORG_A_CODE, EMAIL_FRONTDESK_A1);
        String veterinarian = token(ORG_A_CODE, EMAIL_VET_A1);
        String id = create(receptionist, OffsetDateTime.now().plusDays(610)).path("id").asText();
        patch(receptionist, URL + "/" + id + "/confirm", Map.of());
        patch(receptionist, URL + "/" + id + "/check-in", Map.of());

        JsonNode encounter = patch(veterinarian, URL + "/" + id + "/start",
                Map.of("chiefComplaint", "Kontrol muayenesi"));
        String encounterId = encounter.path("id").asText();
        postJson(veterinarian, CLINIC, "/api/v1/encounters/" + encounterId + "/examination",
                Map.of("examinationSummary", "Genel durumu iyi.")).andExpect(status().isOk());
        postJson(veterinarian, CLINIC, "/api/v1/encounters/" + encounterId + "/diagnosis",
                Map.of("assessment", "Kontrol muayenesi.")).andExpect(status().isOk());
        postJson(veterinarian, CLINIC, "/api/v1/encounters/" + encounterId + "/plan",
                Map.of("treatmentPlan", "Takip gerekmiyor.")).andExpect(status().isOk());

        JsonNode completed = patch(veterinarian, URL + "/" + id + "/complete", Map.of());
        assertThat(completed.path("status").asText()).isEqualTo("COMPLETED");
    }

    private void assertContainsAppointment(JsonNode content, String id) {
        boolean found = false;
        for (JsonNode appointment : content) {
            found |= appointment.path("id").asText().equals(id);
        }
        assertThat(found).isTrue();
    }
}
