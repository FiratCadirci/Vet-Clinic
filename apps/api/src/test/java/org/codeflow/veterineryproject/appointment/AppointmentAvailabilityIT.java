package org.codeflow.veterineryproject.appointment;

import com.fasterxml.jackson.databind.JsonNode;
import org.codeflow.veterineryproject.common.test.ClinicalTestData;
import org.codeflow.veterineryproject.common.test.PatientsApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.codeflow.veterineryproject.common.test.PatientsTestData.*;
import static org.codeflow.veterineryproject.common.test.RegistrationTestData.*;
import static org.codeflow.veterineryproject.common.test.TenantTestData.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** FG-E2: hizmet süresine göre uygun saatler; dolu, geçmiş ve mesai dışı saatler hariç. */
@SpringBootTest
@AutoConfigureMockMvc
class AppointmentAvailabilityIT extends PatientsApiTestSupport {

    private static final String S1 = BRANCH_A1_S1.toString();
    private static final String URL = "/api/v1/appointments/availability";
    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");
    private static final LocalDate DAY = LocalDate.of(2035, 3, 6);

    private String url(UUID service, UUID vet, LocalDate date) {
        return URL + "?serviceId=" + service + "&veterinarianId=" + vet + "&date=" + date;
    }

    private JsonNode availability(String token, UUID service, LocalDate date) throws Exception {
        return body(getJson(token, S1, url(service, SLOT_VET, date)).andExpect(status().isOk()));
    }

    private static List<String> times(JsonNode response) {
        List<String> times = new ArrayList<>();
        response.get("slots").forEach(s -> times.add(s.get("time").asText()));
        return times;
    }

    private OffsetDateTime at(LocalDate date, int hour, int minute) {
        return date.atTime(hour, minute).atZone(ISTANBUL).toOffsetDateTime();
    }

    private String book(String token, LocalDate date, int hour, int minute) throws Exception {
        String client = newClient(token, S1, "Slot", "Sahibi");
        String animal = newAnimal(token, S1, client, "Slot" + hour + minute);
        OffsetDateTime start = at(date, hour, minute);
        return body(postJson(token, S1, "/api/v1/appointments", Map.of("animalId", animal, "veterinarianId", SLOT_VET,
                "startsAt", start.toString(), "endsAt", start.plusMinutes(30).toString()))
                .andExpect(status().isCreated())).get("id").asText();
    }

    @Test
    void slotsFollowServiceDurationAndStayInsideWorkingHours() throws Exception {
        String t = token(ORG_A_CODE, EMAIL_FRONTDESK_A1);

        JsonNode thirty = availability(t, SERVICE_30, DAY);
        assertThat(thirty.get("durationMinutes").asInt()).isEqualTo(30);
        assertThat(thirty.get("date").asText()).isEqualTo(DAY.toString());
        List<String> t30 = times(thirty);
        // 09:00–12:00 ve 13:00–17:00, 15 dakikalık ızgara: sabah 11 + öğleden sonra 15 başlangıç.
        assertThat(t30).hasSize(26).startsWith("09:00").endsWith("16:30").contains("11:30", "13:00")
                .doesNotContain("08:45", "11:45", "12:00", "12:30", "12:45", "16:45", "17:00");
        JsonNode first = thirty.get("slots").get(0);
        assertThat(first.get("startsAt").asText()).isEqualTo("2035-03-06T09:00:00+03:00");
        assertThat(first.get("endsAt").asText()).isEqualTo("2035-03-06T09:30:00+03:00");

        List<String> t45 = times(availability(t, SERVICE_45, DAY));
        assertThat(t45).contains("11:15", "16:15").doesNotContain("11:30", "16:30");
    }

    @Test
    void bookedSlotsAreExcluded_evenInAnotherClinicGroup_butCancelledOnesAreFree() throws Exception {
        String t = token(ORG_A_CODE, EMAIL_FRONTDESK_A1);
        LocalDate day = DAY.plusDays(1);
        String booked = book(t, day, 10, 0);
        // Aynı hekimin başka klinik grubundaki (A2) randevusu RLS ile görünmez ama EXCLUDE organizasyon genelinde
        // geçerli olduğu için o saat de dolu sayılmalıdır.
        insertAppointment(BRANCH_A2_S1, PATIENT_A2_S1, at(day, 14, 0), at(day, 15, 0), "BOOKED");
        insertAppointment(BRANCH_A1_S1, PATIENT_A1_S2, at(day, 16, 0), at(day, 16, 30), "CANCELLED");

        List<String> times = times(availability(t, SERVICE_30, day));
        assertThat(times).doesNotContain("09:45", "10:00", "10:15", "13:45", "14:00", "14:30", "14:45")
                .contains("09:30", "10:30", "13:30", "15:00", "16:00");

        // Ertelemede randevunun kendi saati boş sayılır.
        List<String> rescheduling = times(body(getJson(t, S1, url(SERVICE_30, SLOT_VET, day)
                + "&excludeAppointmentId=" + booked).andExpect(status().isOk())));
        assertThat(rescheduling).contains("10:00").doesNotContain("14:00");
        // Ertelemede hizmet seçilmeyebilir: süre randevunun kendi süresidir (30 dk).
        JsonNode byAppointment = body(getJson(t, S1, URL + "?veterinarianId=" + SLOT_VET + "&date=" + day
                + "&excludeAppointmentId=" + booked).andExpect(status().isOk()));
        assertThat(byAppointment.get("durationMinutes").asInt()).isEqualTo(30);
        assertThat(times(byAppointment)).contains("10:00");
    }

    @Test
    void pastDaysAndPastHoursAreExcluded() throws Exception {
        String t = token(ORG_A_CODE, EMAIL_FRONTDESK_A1);
        LocalDate today = LocalDate.now(ISTANBUL);

        assertThat(availability(t, SERVICE_30, today.minusDays(1)).get("slots")).isEmpty();
        OffsetDateTime now = OffsetDateTime.now();
        for (JsonNode slot : availability(t, SERVICE_30, today).get("slots")) {
            assertThat(OffsetDateTime.parse(slot.get("startsAt").asText())).isAfterOrEqualTo(now.minusMinutes(1));
        }
    }

    @Test
    void veterinarianWithoutWorkingHoursAtTheBranchHasNoSlots() throws Exception {
        String t = token(ORG_A_CODE, EMAIL_FRONTDESK_A1);
        UUID otherVet = UUID.fromString(ClinicalTestData.text(superUserDataSource(), """
                SELECT v.id FROM vet.veterinarians v JOIN vet.staff_members s ON s.id = v.staff_member_id
                WHERE s.user_id = ?""", VET_A1_S2));
        JsonNode response = body(getJson(t, S1, url(SERVICE_30, otherVet, DAY)).andExpect(status().isOk()));
        assertThat(response.get("slots")).isEmpty();
    }

    @Test
    void validationNotFoundAndPermissions() throws Exception {
        String t = token(ORG_A_CODE, EMAIL_FRONTDESK_A1);
        getJson(t, S1, URL + "?veterinarianId=" + SLOT_VET + "&date=" + DAY).andExpect(status().isBadRequest());
        getJson(t, S1, URL + "?serviceId=" + SERVICE_30 + "&veterinarianId=" + SLOT_VET + "&date=2035-13-40")
                .andExpect(status().isBadRequest());
        getJson(t, S1, url(SERVICE_NO_DURATION, SLOT_VET, DAY)).andExpect(status().isUnprocessableEntity());
        getJson(t, S1, url(UUID.randomUUID(), SLOT_VET, DAY)).andExpect(status().isNotFound());
        getJson(t, S1, url(SERVICE_30, UUID.randomUUID(), DAY)).andExpect(status().isNotFound());
        // Başka organizasyonun hizmeti ve başka organizasyon / başka klinik grubunun şubesi RLS ile görünmez: 404.
        getJson(t, S1, url(SERVICE_ORG_B, SLOT_VET, DAY)).andExpect(status().isNotFound());
        getJson(t, S1, url(SERVICE_30, SLOT_VET, DAY) + "&clinicId=" + BRANCH_B1_S1).andExpect(status().isNotFound());
        getJson(t, S1, url(SERVICE_30, SLOT_VET, DAY) + "&clinicId=" + BRANCH_A2_S1).andExpect(status().isNotFound());

        getJson(token(ORG_A_CODE, EMAIL_READER_A1), url(SERVICE_30, SLOT_VET, DAY)).andExpect(status().isForbidden());
        getJson(token(ORG_A_CODE, EMAIL_LIMITED_A1), "/api/v1/appointments/booking-options").andExpect(status().isForbidden());
    }

    @Test
    void bookingOptionsListActiveServicesAndBranchVeterinarians() throws Exception {
        JsonNode options = body(getJson(token(ORG_A_CODE, EMAIL_FRONTDESK_A1), S1, "/api/v1/appointments/booking-options")
                .andExpect(status().isOk()));
        List<String> services = new ArrayList<>();
        options.get("services").forEach(s -> services.add(s.get("id").asText()));
        assertThat(services).contains(SERVICE_30.toString(), SERVICE_45.toString()).doesNotContain(SERVICE_ORG_B.toString());
        List<String> vets = new ArrayList<>();
        options.get("veterinarians").forEach(v -> vets.add(v.get("id").asText()));
        assertThat(vets).contains(SLOT_VET.toString());
    }

    private void insertAppointment(UUID clinic, UUID patient, OffsetDateTime start, OffsetDateTime end, String status) {
        ClinicalTestData.update(superUserDataSource(), """
                INSERT INTO vet.appointments (id, organization_id, clinic_id, client_id, animal_id, veterinarian_id,
                    appointment_no, status, starts_at, ends_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), ORG_A, clinic, patient, patient, SLOT_VET,
                "PT-" + UUID.randomUUID().toString().substring(0, 8), status, start, end);
    }
}
