package org.codeflow.veterineryproject.notification;

import com.fasterxml.jackson.databind.JsonNode;
import org.codeflow.veterineryproject.common.event.DomainEvents.StaffInvitedEvent;
import org.codeflow.veterineryproject.common.event.DomainEvents.StockLowEvent;
import org.codeflow.veterineryproject.notification.push.PushTicket;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.codeflow.veterineryproject.common.test.RegistrationTestData.*;
import static org.codeflow.veterineryproject.common.test.TenantTestData.*;
import static org.codeflow.veterineryproject.notification.NotificationTestData.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** FG-I1 (olay → kanallar), FG-I3 (gönderim kaydı + organizasyon izolasyonu), FG-I4 (bildirim hatası iş işlemini bozmaz). */
class NotificationDeliveryIT extends NotificationItSupport {

    private static final String S1 = BRANCH_A1_S1.toString();

    // ---- FG-I1: olaylar sahibe / personele gider -------------------------------------------------------------------

    @Test
    void appointmentConfirmed_ownerGetsInAppEmailAndPush() throws Exception {
        String token = "ExponentPushToken[owner-confirm]";
        addPushDevice(superUserDataSource(), OWNER_USER_A, token);
        UUID appointment = appointment(superUserDataSource());

        postJson(token(ORG_A_CODE, EMAIL_ADMIN_A1), S1, "/api/v1/appointments/" + appointment + "/confirm", Map.of())
                .andExpect(status().isOk());

        awaitDeliveries("appointment_id = ?", appointment);
        assertThat(statusOf(appointment, "IN_APP")).isEqualTo("SENT");
        assertThat(statusOf(appointment, "EMAIL")).isEqualTo("SENT");
        assertThat(statusOf(appointment, "PUSH")).isEqualTo("SENT");
        assertThat(text("SELECT user_id::text FROM vet.notification_outbox WHERE appointment_id = ? AND channel = 'IN_APP'", appointment))
                .isEqualTo(OWNER_USER_A.toString());
        assertThat(text("SELECT notification_type FROM vet.notification_outbox WHERE appointment_id = ? AND channel = 'IN_APP'", appointment))
                .isEqualTo("APPOINTMENT_CONFIRMED");
        SimpleMailMessage email = lastMailTo(EMAIL_OWNER_A);
        assertThat(email.getSubject()).isEqualTo("Randevunuz onaylandı");
        assertThat(email.getText()).contains(PET_A_NAME, "onaylandı");
        assertThat(push.to(token)).anySatisfy(m -> {
            assertThat(m.title()).isEqualTo("Randevunuz onaylandı");
            assertThat(m.data()).containsEntry("appointmentId", appointment.toString()).containsEntry("type", "APPOINTMENT_CONFIRMED");
        });
    }

    @Test
    void appointmentCancelledAndRescheduled_ownerIsNotified_otherTransitionsAreSilent() throws Exception {
        String admin = token(ORG_A_CODE, EMAIL_ADMIN_A1);
        UUID cancelled = appointment(superUserDataSource());
        UUID moved = appointment(superUserDataSource());
        UUID checkedIn = appointment(superUserDataSource());

        postJson(admin, S1, "/api/v1/appointments/" + cancelled + "/cancel", Map.of("cancellationReason", "Sahip istedi"))
                .andExpect(status().isOk());
        OffsetDateTime start = OffsetDateTime.now().plusDays(300).withNano(0);
        postJson(admin, S1, "/api/v1/appointments/" + moved + "/reschedule", Map.of("startsAt", start.toString(), "endsAt", start.plusHours(1).toString()))
                .andExpect(status().isOk());
        postJson(admin, S1, "/api/v1/appointments/" + checkedIn + "/check-in", Map.of()).andExpect(status().isOk());

        awaitDeliveries("appointment_id = ?", cancelled);
        awaitDeliveries("appointment_id = ?", moved);
        assertThat(text("SELECT notification_type FROM vet.notification_outbox WHERE appointment_id = ? AND channel = 'IN_APP'", cancelled))
                .isEqualTo("APPOINTMENT_CANCELLED");
        assertThat(text("SELECT notification_type FROM vet.notification_outbox WHERE appointment_id = ? AND channel = 'IN_APP'", moved))
                .isEqualTo("APPOINTMENT_RESCHEDULED");
        assertThat(text("SELECT message_body FROM vet.notification_outbox WHERE appointment_id = ? AND channel = 'IN_APP'", moved))
                .contains("yeni tarihi");
        Thread.sleep(300);
        assertThat(count("SELECT count(*) FROM vet.notification_outbox WHERE appointment_id = ?", checkedIn)).isZero();
    }

    @Test
    void vaccineRecorded_ownerGetsNextDoseDate_ownerWithoutAppAccountGetsEmailOnly() throws Exception {
        String vet = token(ORG_A_CODE, EMAIL_VET_A1);
        LocalDate next = LocalDate.now().plusDays(30);
        String encounter = body(postJson(vet, S1, "/api/v1/encounters", Map.of("animalId", PET_A.toString(), "chiefComplaint", "Aşı"))
                .andExpect(status().isCreated())).get("id").asText();
        postJson(vet, S1, "/api/v1/vaccinations", Map.of("encounterId", encounter, "vaccineName", "Kuduz-NT", "nextDueAt", next.toString()))
                .andExpect(status().isCreated());
        String encounter2 = body(postJson(vet, S1, "/api/v1/encounters", Map.of("animalId", PET_A2.toString(), "chiefComplaint", "Aşı"))
                .andExpect(status().isCreated())).get("id").asText();
        postJson(vet, S1, "/api/v1/vaccinations", Map.of("encounterId", encounter2, "vaccineName", "Karma-NT"))
                .andExpect(status().isCreated());

        awaitDeliveries("animal_id = ? AND notification_type = 'VACCINE_RECORDED'", PET_A);
        awaitDeliveries("animal_id = ? AND notification_type = 'VACCINE_RECORDED'", PET_A2);
        String inApp = text("SELECT message_body FROM vet.notification_outbox WHERE animal_id = ? AND notification_type = 'VACCINE_RECORDED' AND channel = 'IN_APP' AND message_body LIKE '%Kuduz-NT%'", PET_A);
        assertThat(inApp).contains(PET_A_NAME, "Kuduz-NT", "Sonraki doz:", String.valueOf(next.getYear()));
        assertThat(lastMailTo(EMAIL_OWNER_A).getText()).contains("Kuduz-NT");
        assertThat(count("SELECT count(*) FROM vet.notification_outbox WHERE animal_id = ? AND notification_type = 'VACCINE_RECORDED' AND channel <> 'EMAIL'", PET_A2))
                .isZero();
        assertThat(lastMailTo(EMAIL_OWNER_A2).getText()).contains("Boncuk", "Karma-NT");
    }

    @Test
    void stockLow_onlyStockStaffOfThatBranchGetInAppAndCanReadIt() throws Exception {
        String product = "Amoksisilin-" + UUID.randomUUID().toString().substring(0, 6);
        publishInTransaction(ORG_A, STOCK_A1, BRANCH_A1_S1,
                new StockLowEvent(ORG_A, BRANCH_A1_S1, UUID.randomUUID(), product, new BigDecimal("2.000"), new BigDecimal("10")));

        awaitDeliveries("notification_type = 'STOCK_LOW' AND message_body LIKE ?", "%" + product + "%");
        assertThat(count("SELECT count(*) FROM vet.notification_outbox WHERE message_body LIKE ? AND user_id = ? AND channel = 'IN_APP' AND status = 'SENT'",
                "%" + product + "%", STOCK_A1)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM vet.notification_outbox WHERE message_body LIKE ? AND user_id = ?", "%" + product + "%", STOCK_A1_S2)).isZero();
        assertThat(count("SELECT count(*) FROM vet.notification_outbox WHERE message_body LIKE ? AND channel <> 'IN_APP'", "%" + product + "%")).isZero();

        String stock = token(ORG_A_CODE, EMAIL_STOCK_A1);
        JsonNode list = body(getJson(stock, "/api/v1/notifications?unreadOnly=true&size=50").andExpect(status().isOk()));
        JsonNode item = StreamSupport.stream(list.get("content").spliterator(), false)
                .filter(n -> n.get("body").asText().contains(product)).findFirst().orElseThrow();
        assertThat(item.get("title").asText()).isEqualTo("Kritik stok uyarısı");
        assertThat(item.get("body").asText()).contains("2 kaldı", "minimum 10");
        assertThat(item.get("readAt").isNull()).isTrue();
        long unread = body(getJson(stock, "/api/v1/notifications/unread-count").andExpect(status().isOk())).get("unreadCount").asLong();
        assertThat(unread).isGreaterThanOrEqualTo(1);

        String id = item.get("id").asText();
        patchJson(token(ORG_A_CODE, EMAIL_STOCK_A1_S2), "/api/v1/notifications/" + id + "/read", Map.of()).andExpect(status().isNotFound());
        patchJson(token(ORG_B_CODE, EMAIL_ADMIN_B1), "/api/v1/notifications/" + id + "/read", Map.of()).andExpect(status().isNotFound());
        JsonNode read = body(patchJson(stock, "/api/v1/notifications/" + id + "/read", Map.of()).andExpect(status().isOk()));
        assertThat(read.get("readAt").isNull()).isFalse();
        body(postJson(stock, "/api/v1/notifications/read-all", Map.of()).andExpect(status().isOk()));
        assertThat(body(getJson(stock, "/api/v1/notifications/unread-count")).get("unreadCount").asLong()).isZero();
    }

    @Test
    void staffInvite_emailContainsAcceptLinkAndClinicName_tokenIsNotStoredInLog() {
        String email = "davet-" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
        String inviteToken = "tok+" + UUID.randomUUID();
        publishInTransaction(ORG_A, ADMIN_A1, BRANCH_A1_S1,
                new StaffInvitedEvent(ORG_A, UUID.randomUUID(), email, inviteToken, "Pati Kliniği", ADMIN_A1));

        awaitDeliveries("recipient = ?", email);
        assertThat(text("SELECT status FROM vet.notification_outbox WHERE recipient = ?", email)).isEqualTo("SENT");
        assertThat(text("SELECT notification_type FROM vet.notification_outbox WHERE recipient = ?", email)).isEqualTo("STAFF_INVITED");
        SimpleMailMessage mailMessage = lastMailTo(email);
        assertThat(mailMessage.getSubject()).isEqualTo("Pati Kliniği sizi VetClinic'e davet etti");
        assertThat(mailMessage.getFrom()).contains("no-reply@vetclinic.local");
        String encoded = java.net.URLEncoder.encode(inviteToken, java.nio.charset.StandardCharsets.UTF_8);
        assertThat(mailMessage.getText()).contains("http://localhost:5173/accept-invite?token=" + encoded, "Pati Kliniği", "davet etti");
        assertThat(count("SELECT count(*) FROM vet.notification_outbox WHERE message_body LIKE ?", "%" + inviteToken.substring(4) + "%")).isZero();
    }

    // ---- FG-I4 / FOG-4: bildirim hatası iş işlemini bozmaz -----------------------------------------------------------

    @Test
    void smtpDown_appointmentStillConfirmed_emailLoggedAsFailed_inAppStillSent() throws Exception {
        mail.failing = true;
        UUID appointment = appointment(superUserDataSource());

        JsonNode confirmed = body(postJson(token(ORG_A_CODE, EMAIL_ADMIN_A1), S1, "/api/v1/appointments/" + appointment + "/confirm", Map.of())
                .andExpect(status().isOk()));

        assertThat(confirmed.get("status").asText()).isEqualTo("CONFIRMED");
        awaitDeliveries("appointment_id = ?", appointment);
        assertThat(statusOf(appointment, "EMAIL")).isEqualTo("FAILED");
        assertThat(text("SELECT last_error FROM vet.notification_outbox WHERE appointment_id = ? AND channel = 'EMAIL'", appointment))
                .contains("Connection refused");
        assertThat(statusOf(appointment, "IN_APP")).isEqualTo("SENT");
        assertThat(text("SELECT status FROM vet.appointments WHERE id = ?", appointment)).isEqualTo("CONFIRMED");
    }

    @Test
    void deviceNotRegistered_tokenIsDeleted_pushFailed_appointmentUnaffected() throws Exception {
        String dead = "ExponentPushToken[dead-device]";
        addPushDevice(superUserDataSource(), OWNER_USER_A, dead);
        push.errors.put(dead, PushTicket.error("\"" + dead + "\" is not a registered push notification recipient", PushTicket.DEVICE_NOT_REGISTERED));
        superUserDelete("DELETE FROM vet.push_devices WHERE user_id = ? AND expo_push_token <> ?", OWNER_USER_A, dead);
        UUID appointment = appointment(superUserDataSource());

        postJson(token(ORG_A_CODE, EMAIL_ADMIN_A1), S1, "/api/v1/appointments/" + appointment + "/confirm", Map.of())
                .andExpect(status().isOk());

        awaitDeliveries("appointment_id = ?", appointment);
        assertThat(statusOf(appointment, "PUSH")).isEqualTo("FAILED");
        assertThat(text("SELECT last_error FROM vet.notification_outbox WHERE appointment_id = ? AND channel = 'PUSH'", appointment))
                .contains("DeviceNotRegistered");
        assertThat(count("SELECT count(*) FROM vet.push_devices WHERE expo_push_token = ?", dead)).isZero();
        assertThat(text("SELECT status FROM vet.appointments WHERE id = ?", appointment)).isEqualTo("CONFIRMED");
    }

    // ---- FG-I3: gönderim kaydı -----------------------------------------------------------------------------------------

    @Test
    void deliveries_adminSeesOwnOrganizationFilteredAndPaged_otherOrgAndUnauthorizedCannot() throws Exception {
        mail.failing = true;
        UUID appointment = appointment(superUserDataSource());
        postJson(token(ORG_A_CODE, EMAIL_ADMIN_A1), S1, "/api/v1/appointments/" + appointment + "/confirm", Map.of()).andExpect(status().isOk());
        awaitDeliveries("appointment_id = ?", appointment);
        String admin = token(ORG_A_CODE, EMAIL_ADMIN_A1);

        JsonNode failedEmails = body(getJson(admin, "/api/v1/notifications/deliveries?channel=EMAIL&status=FAILED&type=APPOINTMENT_CONFIRMED&size=200")
                .andExpect(status().isOk()));
        JsonNode row = StreamSupport.stream(failedEmails.get("content").spliterator(), false)
                .filter(d -> EMAIL_OWNER_A.equals(d.get("recipientEmail").asText(null))).findFirst().orElseThrow();
        assertThat(row.get("channel").asText()).isEqualTo("EMAIL");
        assertThat(row.get("status").asText()).isEqualTo("FAILED");
        assertThat(row.get("recipientName").asText()).isEqualTo("Ayşe Sahip");
        assertThat(row.get("error").asText()).contains("Connection refused");
        assertThat(row.get("attemptCount").asInt()).isEqualTo(1);
        assertThat(StreamSupport.stream(failedEmails.get("content").spliterator(), false))
                .allSatisfy(d -> assertThat(d.get("status").asText()).isEqualTo("FAILED"));

        JsonNode page = body(getJson(admin, "/api/v1/notifications/deliveries?size=1&page=0").andExpect(status().isOk()));
        assertThat(page.get("content")).hasSize(1);
        assertThat(page.get("totalElements").asLong()).isGreaterThan(1);
        String today = LocalDate.now().toString();
        JsonNode summary = body(getJson(admin, "/api/v1/notifications/deliveries/summary?from=" + today + "&to=" + today).andExpect(status().isOk()));
        assertThat(StreamSupport.stream(summary.spliterator(), false))
                .anySatisfy(s -> {
                    assertThat(s.get("channel").asText()).isEqualTo("EMAIL");
                    assertThat(s.get("status").asText()).isEqualTo("FAILED");
                    assertThat(s.get("count").asLong()).isPositive();
                });

        JsonNode otherOrg = body(getJson(token(ORG_B_CODE, EMAIL_ADMIN_B1), "/api/v1/notifications/deliveries?size=200").andExpect(status().isOk()));
        assertThat(StreamSupport.stream(otherOrg.get("content").spliterator(), false).map(d -> d.get("id").asText()))
                .doesNotContain(row.get("id").asText())
                .allSatisfy(id -> assertThat(text("SELECT organization_id::text FROM vet.notification_outbox WHERE id = ?::uuid", id))
                        .isEqualTo(ORG_B.toString()));
        getJson(token(ORG_A_CODE, EMAIL_RECEPTION_NOTIF_A1), "/api/v1/notifications/deliveries").andExpect(status().isForbidden());
        getJson(admin, "/api/v1/notifications/deliveries?channel=FAX").andExpect(status().isBadRequest());
        getJson(admin, "/api/v1/notifications/deliveries?from=2026-02-10&to=2026-02-01").andExpect(status().isBadRequest());
    }

    // ---- yardımcılar ---------------------------------------------------------------------------------------------------

    private static String statusOf(UUID appointment, String channel) {
        return text("SELECT status FROM vet.notification_outbox WHERE appointment_id = ? AND channel = ?", appointment, channel);
    }

    private SimpleMailMessage lastMailTo(String address) {
        List<SimpleMailMessage> messages = mail.to(address);
        assertThat(messages).as("e-posta: " + address).isNotEmpty();
        return messages.get(messages.size() - 1);
    }

    private static void superUserDelete(String sql, Object... params) {
        org.codeflow.veterineryproject.common.test.ClinicalTestData.update(superUserDataSource(), sql, params);
    }
}
