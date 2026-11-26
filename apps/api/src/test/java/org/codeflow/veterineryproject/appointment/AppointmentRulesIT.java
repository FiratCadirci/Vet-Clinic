package org.codeflow.veterineryproject.appointment;

import com.fasterxml.jackson.databind.JsonNode;
import org.codeflow.veterineryproject.common.test.ClinicalTestData;
import org.codeflow.veterineryproject.common.test.PatientsApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.codeflow.veterineryproject.common.test.PatientsTestData.SLOT_VET;
import static org.codeflow.veterineryproject.common.test.RegistrationTestData.*;
import static org.codeflow.veterineryproject.common.test.TenantTestData.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** FG-E3 (eşzamanlı çakışma), FG-E4 (talep/onay/erteleme/iptal/gelmedi, geçersiz geçiş 409) ve vefat kuralları. */
@SpringBootTest
@AutoConfigureMockMvc
class AppointmentRulesIT extends PatientsApiTestSupport {

    private static final String S1 = BRANCH_A1_S1.toString();
    private static final String URL = "/api/v1/appointments";
    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");
    private static final LocalDate DAY = LocalDate.of(2035, 4, 10);

    private static OffsetDateTime at(LocalDate date, int hour, int minute) {
        return date.atTime(hour, minute).atZone(ISTANBUL).toOffsetDateTime();
    }

    private static Map<String, Object> request(String animal, UUID vet, OffsetDateTime start) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("animalId", animal);
        if (vet != null) {
            body.put("veterinarianId", vet);
        }
        body.put("startsAt", start.toString());
        body.put("endsAt", start.plusMinutes(30).toString());
        return body;
    }

    private String patient(String token, String name) throws Exception {
        return newAnimal(token, S1, newClient(token, S1, name, "Sahip"), name);
    }

    private String create(String token, String animal, OffsetDateTime start) throws Exception {
        return body(postJson(token, S1, URL, request(animal, null, start)).andExpect(status().isCreated())).get("id").asText();
    }

    @Test
    void concurrentConflictingRequestsForTheSameVet_onlyOneSucceeds() throws Exception {
        String t = token(ORG_A_CODE, EMAIL_FRONTDESK_A1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            int[] hours = {9, 10, 11, 13};
            for (int round = 0; round < hours.length; round++) {
                OffsetDateTime slot = at(DAY, hours[round], 0);
                List<String> animals = List.of(patient(t, "Yaris" + round + "a"), patient(t, "Yaris" + round + "b"));
                CountDownLatch start = new CountDownLatch(1);
                List<Callable<MvcResult>> jobs = new ArrayList<>();
                for (String animal : animals) {
                    jobs.add(() -> {
                        start.await();
                        return postJson(t, S1, URL, request(animal, SLOT_VET, slot)).andReturn();
                    });
                }
                List<Future<MvcResult>> futures = new ArrayList<>();
                for (Callable<MvcResult> job : jobs) {
                    futures.add(pool.submit(job));
                }
                start.countDown();
                List<Integer> statuses = new ArrayList<>();
                for (Future<MvcResult> f : futures) {
                    MvcResult result = f.get();
                    statuses.add(result.getResponse().getStatus());
                    if (result.getResponse().getStatus() == 409) {
                        assertThat(result.getResponse().getContentAsString()).contains("APPOINTMENT_TIME_CONFLICT");
                    }
                }
                assertThat(statuses).containsExactlyInAnyOrder(201, 409);
                assertThat(ClinicalTestData.count(superUserDataSource(),
                        "SELECT count(*) FROM vet.appointments WHERE veterinarian_id = ? AND starts_at = ?", SLOT_VET, slot))
                        .isEqualTo(1);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void sameVetOverlapIs409_andAdjacentSlotIsAllowed() throws Exception {
        String t = token(ORG_A_CODE, EMAIL_FRONTDESK_A1);
        OffsetDateTime slot = at(DAY, 15, 0);
        postJson(t, S1, URL, request(patient(t, "Komsu1"), SLOT_VET, slot)).andExpect(status().isCreated());
        postJson(t, S1, URL, request(patient(t, "Komsu2"), SLOT_VET, slot.plusMinutes(15))).andExpect(status().isConflict());
        postJson(t, S1, URL, request(patient(t, "Komsu3"), SLOT_VET, slot.plusMinutes(30))).andExpect(status().isCreated());
    }

    @Test
    void invalidStatusTransitionsAre409() throws Exception {
        String t = token(ORG_A_CODE, EMAIL_FRONTDESK_A1);
        String animal = patient(t, "Gecis");

        String cancelled = create(t, animal, at(DAY.plusDays(1), 10, 0));
        patch(t, S1, URL + "/" + cancelled + "/cancel", Map.of("cancellationReason", "Sahibi vazgeçti"))
                .andExpect(status().isOk());
        JsonNode error = body(patch(t, S1, URL + "/" + cancelled + "/confirm", Map.of()).andExpect(status().isConflict()));
        assertThat(error.get("code").asText()).isEqualTo("INVALID_STATUS_TRANSITION");
        assertThat(error.get("details").get("from").asText()).isEqualTo("CANCELLED");
        patch(t, S1, URL + "/" + cancelled + "/check-in", Map.of()).andExpect(status().isConflict());
        patch(t, S1, URL + "/" + cancelled + "/no-show", Map.of()).andExpect(status().isConflict());
        patch(t, S1, URL + "/" + cancelled + "/cancel", Map.of("cancellationReason", "tekrar")).andExpect(status().isConflict());
        OffsetDateTime later = at(DAY.plusDays(2), 10, 0);
        patch(t, S1, URL + "/" + cancelled + "/reschedule", Map.of("startsAt", later.toString(),
                "endsAt", later.plusMinutes(30).toString())).andExpect(status().isConflict());

        String checkedIn = create(t, animal, at(DAY.plusDays(1), 11, 0));
        patch(t, S1, URL + "/" + checkedIn + "/check-in", Map.of()).andExpect(status().isOk());
        patch(t, S1, URL + "/" + checkedIn + "/confirm", Map.of()).andExpect(status().isConflict());
        patch(t, S1, URL + "/" + checkedIn + "/no-show", Map.of()).andExpect(status().isConflict());

        String noShow = create(t, animal, at(DAY.plusDays(1), 12, 0));
        patch(t, S1, URL + "/" + noShow + "/no-show", Map.of()).andExpect(status().isOk());
        patch(t, S1, URL + "/" + noShow + "/check-in", Map.of()).andExpect(status().isConflict());
        // Durum değişmemiştir.
        assertThat(ClinicalTestData.appointmentStatus(superUserDataSource(), UUID.fromString(noShow))).isEqualTo("NO_SHOW");
    }

    @Test
    void requestConfirmRescheduleAndCancelFlow() throws Exception {
        String t = token(ORG_A_CODE, EMAIL_FRONTDESK_A1);
        String animal = patient(t, "Talep");
        // Mobil talep (REQUESTED) personel tarafından onaylanır, ertelenir ve gerekçeyle iptal edilir.
        UUID requested = UUID.randomUUID();
        OffsetDateTime start = at(DAY.plusDays(3), 10, 0);
        ClinicalTestData.update(superUserDataSource(), """
                INSERT INTO vet.appointments (id, organization_id, clinic_id, client_id, animal_id, appointment_no, status,
                    booking_channel, starts_at, ends_at)
                SELECT ?, ?, ?, ao.client_id, ao.animal_id, ?, 'REQUESTED', 'MOBILE', ?, ?
                FROM vet.animal_owners ao WHERE ao.animal_id = ? AND ao.valid_to IS NULL LIMIT 1
                """, requested, ORG_A, BRANCH_A1_S1, "PT-REQ-" + requested.toString().substring(0, 6), start,
                start.plusMinutes(30), UUID.fromString(animal));

        assertThat(body(patch(t, S1, URL + "/" + requested + "/confirm", Map.of()).andExpect(status().isOk()))
                .get("status").asText()).isEqualTo("CONFIRMED");
        OffsetDateTime moved = start.plusDays(1);
        JsonNode rescheduled = body(patch(t, S1, URL + "/" + requested + "/reschedule", Map.of("startsAt", moved.toString(),
                "endsAt", moved.plusMinutes(30).toString())).andExpect(status().isOk()));
        assertThat(OffsetDateTime.parse(rescheduled.get("startsAt").asText())).isEqualTo(moved);
        patch(t, S1, URL + "/" + requested + "/cancel", Map.of()).andExpect(status().isBadRequest());
        JsonNode cancelled = body(patch(t, S1, URL + "/" + requested + "/cancel",
                Map.of("cancellationReason", "Sahibi aradı")).andExpect(status().isOk()));
        assertThat(cancelled.get("cancellationReason").asText()).isEqualTo("Sahibi aradı");
    }

    @Test
    void deceasedPatient_cannotGetAppointmentOrEncounter() throws Exception {
        String frontdesk = token(ORG_A_CODE, EMAIL_FRONTDESK_A1);
        String vet = token(ORG_A_CODE, EMAIL_VET_A1);
        String animal = patient(frontdesk, "Vefat");
        String appointment = create(frontdesk, animal, at(DAY.plusDays(5), 10, 0));
        patch(frontdesk, S1, URL + "/" + appointment + "/check-in", Map.of()).andExpect(status().isOk());

        postJson(frontdesk, S1, "/api/v1/animals/" + animal + "/deceased", Map.of("deceasedAt", LocalDate.now().toString()))
                .andExpect(status().isOk());

        JsonNode noAppointment = body(postJson(frontdesk, S1, URL, request(animal, null, at(DAY.plusDays(6), 10, 0)))
                .andExpect(status().isConflict()));
        assertThat(noAppointment.get("code").asText()).isEqualTo("PATIENT_DECEASED");
        JsonNode noWalkIn = body(postJson(vet, S1, "/api/v1/encounters", Map.of("animalId", animal, "chiefComplaint", "x"))
                .andExpect(status().isConflict()));
        assertThat(noWalkIn.get("code").asText()).isEqualTo("PATIENT_DECEASED");
        patch(vet, S1, URL + "/" + appointment + "/start", Map.of("chiefComplaint", "x")).andExpect(status().isConflict());
        postJson(vet, S1, URL + "/" + appointment + "/encounter", Map.of()).andExpect(status().isConflict());
        // Başarısız başlatma randevu durumunu değiştirmez.
        assertThat(ClinicalTestData.appointmentStatus(superUserDataSource(), UUID.fromString(appointment))).isEqualTo("CHECKED_IN");
    }

    @Test
    void otherOrganizationGets404AndUnauthorizedRoleGets403() throws Exception {
        String t = token(ORG_A_CODE, EMAIL_FRONTDESK_A1);
        String id = create(t, patient(t, "Izolasyon"), at(DAY.plusDays(7), 10, 0));
        ClinicalTestData.update(superUserDataSource(), """
                INSERT INTO vet.role_permissions (organization_id, role_id, permission_id)
                SELECT ?, ?, id FROM vet.permissions WHERE code = 'appointments.manage' ON CONFLICT DO NOTHING
                """, ORG_B, ROLE_MANAGER_B);
        String orgB = token(ORG_B_CODE, EMAIL_MANAGER_B1);
        patch(orgB, BRANCH_B1_S1.toString(), URL + "/" + id + "/confirm", Map.of()).andExpect(status().isNotFound());
        getJson(orgB, URL + "/" + id).andExpect(status().isNotFound());
        patch(token(ORG_A_CODE, EMAIL_READER_A1), BRANCH_A1_S2.toString(), URL + "/" + id + "/confirm", Map.of())
                .andExpect(status().isForbidden());
    }
}
