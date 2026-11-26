package org.codeflow.veterineryproject.notification;

import com.fasterxml.jackson.databind.JsonNode;
import org.codeflow.veterineryproject.common.event.DomainEvents.StockLowEvent;
import org.codeflow.veterineryproject.common.test.ClinicalTestData;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.codeflow.veterineryproject.common.test.TenantTestData.*;
import static org.codeflow.veterineryproject.notification.NotificationTestData.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** FG-I2: kullanıcı olay türü × kanal bazında tercihlerini yönetir; kapalı kanala gönderim yapılmaz. */
class NotificationPreferenceIT extends NotificationItSupport {

    @Test
    void staffSeesOnlyStaffTypes_defaultsAreEnabled() throws Exception {
        JsonNode prefs = body(getJson(token(ORG_A_CODE, EMAIL_STOCK_A1_S2), "/api/v1/notifications/preferences").andExpect(status().isOk()));

        List<String> types = StreamSupport.stream(prefs.spliterator(), false).map(p -> p.get("type").asText()).toList();
        assertThat(types).contains("STOCK_LOW").doesNotContain("VACCINE_DUE", "STAFF_INVITED");
        JsonNode stock = find(prefs, "STOCK_LOW");
        assertThat(stock.get("label").asText()).isEqualTo("Kritik stok uyarısı");
        assertThat(stock.get("channels")).hasSize(1);
        assertThat(stock.get("channels").get(0).get("channel").asText()).isEqualTo("IN_APP");
        assertThat(stock.get("channels").get(0).get("enabled").asBoolean()).isTrue();
    }

    @Test
    void disabledInAppPreference_stockAlertIsSkippedForThatUserOnly() throws Exception {
        String stock = token(ORG_A_CODE, EMAIL_STOCK_A1);
        JsonNode updated = body(putJson(stock, "/api/v1/notifications/preferences",
                Map.of("preferences", List.of(Map.of("type", "STOCK_LOW", "channel", "IN_APP", "enabled", false)))).andExpect(status().isOk()));
        assertThat(find(updated, "STOCK_LOW").get("channels").get(0).get("enabled").asBoolean()).isFalse();

        // Aynı şubeye ikinci bir stok yetkilisi: onun tercihi açık kalır.
        UUID colleague = UUID.randomUUID();
        ClinicalTestData.update(superUserDataSource(), "INSERT INTO vet.app_users (id, organization_id, email, password_hash, display_name) VALUES (?, ?, ?, 'x', 'Stok 2')",
                colleague, ORG_A, "notif-stock2-" + colleague.toString().substring(0, 6) + "@test.com");
        ClinicalTestData.update(superUserDataSource(), "INSERT INTO vet.user_clinic_roles (organization_id, user_id, clinic_id, role_id, valid_from) VALUES (?, ?, ?, ?, CURRENT_DATE - 1)",
                ORG_A, colleague, BRANCH_A1_S1, ROLE_STOCK_A);
        String product = "Pref-" + UUID.randomUUID().toString().substring(0, 6);
        publishInTransaction(ORG_A, STOCK_A1, BRANCH_A1_S1,
                new StockLowEvent(ORG_A, BRANCH_A1_S1, UUID.randomUUID(), product, BigDecimal.ONE, BigDecimal.TEN));

        awaitDeliveries("message_body LIKE ?", "%" + product + "%");
        assertThat(text("SELECT status FROM vet.notification_outbox WHERE message_body LIKE ? AND user_id = ?", "%" + product + "%", STOCK_A1))
                .isEqualTo("SKIPPED");
        assertThat(text("SELECT last_error FROM vet.notification_outbox WHERE message_body LIKE ? AND user_id = ?", "%" + product + "%", STOCK_A1))
                .isEqualTo("Kullanıcı tercihi kapalı.");
        assertThat(text("SELECT status FROM vet.notification_outbox WHERE message_body LIKE ? AND user_id = ?", "%" + product + "%", colleague))
                .isEqualTo("SENT");
        JsonNode inbox = body(getJson(stock, "/api/v1/notifications?size=100").andExpect(status().isOk()));
        assertThat(StreamSupport.stream(inbox.get("content").spliterator(), false)).noneMatch(n -> n.get("body").asText().contains(product));

        putJson(stock, "/api/v1/notifications/preferences",
                Map.of("preferences", List.of(Map.of("type", "STOCK_LOW", "channel", "IN_APP", "enabled", true)))).andExpect(status().isOk());
        assertThat(count("SELECT count(*) FROM vet.notification_preferences WHERE user_id = ? AND notification_type = 'STOCK_LOW'", STOCK_A1)).isEqualTo(1);
    }

    @Test
    void ownerDisabledPush_pushIsNotSentButInAppAndEmailAre() throws Exception {
        String token = "ExponentPushToken[owner-pref]";
        addPushDevice(superUserDataSource(), OWNER_USER_A, token);
        ClinicalTestData.update(superUserDataSource(), "INSERT INTO vet.notification_preferences (organization_id, user_id, notification_type, channel, is_enabled) "
                + "VALUES (?, ?, 'APPOINTMENT_CONFIRMED', 'PUSH', false)", ORG_A, OWNER_USER_A);
        UUID appointment = appointment(superUserDataSource());

        postJson(token(ORG_A_CODE, EMAIL_ADMIN_A1), BRANCH_A1_S1.toString(), "/api/v1/appointments/" + appointment + "/confirm", Map.of())
                .andExpect(status().isOk());

        awaitDeliveries("appointment_id = ?", appointment);
        assertThat(text("SELECT status FROM vet.notification_outbox WHERE appointment_id = ? AND channel = 'PUSH'", appointment)).isEqualTo("SKIPPED");
        assertThat(text("SELECT status FROM vet.notification_outbox WHERE appointment_id = ? AND channel = 'IN_APP'", appointment)).isEqualTo("SENT");
        assertThat(text("SELECT status FROM vet.notification_outbox WHERE appointment_id = ? AND channel = 'EMAIL'", appointment)).isEqualTo("SENT");
        assertThat(push.to(token)).isEmpty();
    }

    @Test
    void invalidPreferenceUpdatesAreRejectedWith400() throws Exception {
        String stock = token(ORG_A_CODE, EMAIL_STOCK_A1);

        putJson(stock, "/api/v1/notifications/preferences",
                Map.of("preferences", List.of(Map.of("type", "BOGUS", "channel", "IN_APP", "enabled", false)))).andExpect(status().isBadRequest());
        putJson(stock, "/api/v1/notifications/preferences",
                Map.of("preferences", List.of(Map.of("type", "STOCK_LOW", "channel", "EMAIL", "enabled", false)))).andExpect(status().isBadRequest());
        putJson(stock, "/api/v1/notifications/preferences",
                Map.of("preferences", List.of(Map.of("type", "STAFF_INVITED", "channel", "EMAIL", "enabled", false)))).andExpect(status().isBadRequest());
        putJson(stock, "/api/v1/notifications/preferences",
                Map.of("preferences", List.of(Map.of("type", "STOCK_LOW", "channel", "IN_APP")))).andExpect(status().isBadRequest());
        putJson(stock, "/api/v1/notifications/preferences", Map.of()).andExpect(status().isBadRequest());
        assertThat(count("SELECT count(*) FROM vet.notification_preferences WHERE user_id = ? AND is_enabled = false", STOCK_A1)).isZero();
    }

    private org.springframework.test.web.servlet.ResultActions putJson(String token, String url, Object body) throws Exception {
        return mvc.perform(put(url).header("Authorization", "Bearer " + token)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
    }

    private static JsonNode find(JsonNode prefs, String type) {
        return StreamSupport.stream(prefs.spliterator(), false).filter(p -> type.equals(p.get("type").asText())).findFirst().orElseThrow();
    }
}
