package org.codeflow.veterineryproject.notification;

import com.fasterxml.jackson.databind.JsonNode;
import org.codeflow.veterineryproject.notification.service.VaccineReminderJob;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.codeflow.veterineryproject.common.test.RegistrationTestData.EMAIL_VET_A1;
import static org.codeflow.veterineryproject.common.test.TenantTestData.*;
import static org.codeflow.veterineryproject.notification.NotificationTestData.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** FG-D5: yaklaşan (3 gün) / geciken aşılar için sahibe bir kez hatırlatma; elle tetikleme ve eşzamanlı koşu. */
class VaccineReminderIT extends NotificationItSupport {

    private static final String S1 = BRANCH_A1_S1.toString();

    @Autowired
    private VaccineReminderJob job;

    @Test
    void manualRun_upcomingAndOverdue_ownerNotifiedOnceOnAllChannels_secondRunSendsNothing() throws Exception {
        String upcoming = recordVaccine("Hatırlatma-Y-" + suffix(), null, LocalDate.now().plusDays(2));
        String overdue = recordVaccine("Hatırlatma-G-" + suffix(), OffsetDateTime.now().minusDays(60), LocalDate.now().minusDays(4));
        String later = recordVaccine("Hatırlatma-L-" + suffix(), null, LocalDate.now().plusDays(10));
        String admin = token(ORG_A_CODE, EMAIL_ADMIN_A1);

        JsonNode first = body(postJson(admin, "/api/v1/notifications/run-vaccine-reminders", Map.of()).andExpect(status().isOk()));
        assertThat(first.get("processed").asInt()).isGreaterThanOrEqualTo(2);
        body(postJson(admin, "/api/v1/notifications/run-vaccine-reminders", Map.of()).andExpect(status().isOk()));

        assertThat(dispatches(upcoming)).isEqualTo(1);
        assertThat(dispatches(overdue)).isEqualTo(1);
        assertThat(dispatches(later)).isZero();
        assertThat(text("SELECT reminder_kind FROM vet.vaccine_reminder_dispatches WHERE vaccine_record_id = ?::uuid", upcoming)).isEqualTo("UPCOMING");
        assertThat(text("SELECT reminder_kind FROM vet.vaccine_reminder_dispatches WHERE vaccine_record_id = ?::uuid", overdue)).isEqualTo("OVERDUE");
        String upcomingName = vaccineName(upcoming);
        String overdueName = vaccineName(overdue);
        assertThat(reminders("IN_APP", upcomingName)).isEqualTo(1);
        assertThat(reminders("EMAIL", upcomingName)).isEqualTo(1);
        assertThat(reminders("IN_APP", overdueName)).isEqualTo(1);
        assertThat(text("SELECT subject FROM vet.notification_outbox WHERE notification_type = 'VACCINE_DUE' AND channel = 'IN_APP' AND message_body LIKE ?", "%" + overdueName + "%"))
                .isEqualTo("Aşı tarihi geçti");
        assertThat(text("SELECT message_body FROM vet.notification_outbox WHERE notification_type = 'VACCINE_DUE' AND channel = 'IN_APP' AND message_body LIKE ?", "%" + upcomingName + "%"))
                .contains(PET_A_NAME, "yapılmalı");
        assertThat(text("SELECT user_id::text FROM vet.notification_outbox WHERE notification_type = 'VACCINE_DUE' AND channel = 'IN_APP' AND message_body LIKE ?", "%" + upcomingName + "%"))
                .isEqualTo(OWNER_USER_A.toString());
        assertThat(mail.to(EMAIL_OWNER_A)).anySatisfy(m -> assertThat(m.getText()).contains(upcomingName));
    }

    @Test
    void concurrentScheduledRuns_produceASingleReminder() throws Exception {
        String record = recordVaccine("Eşzamanlı-" + suffix(), null, LocalDate.now().plusDays(1));
        String name = vaccineName(record);
        int runs = 4;
        ExecutorService pool = Executors.newFixedThreadPool(runs);
        CountDownLatch start = new CountDownLatch(1);
        try {
            java.util.List<Future<?>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < runs; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    job.runDaily();
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> f : futures) {
                f.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(dispatches(record)).isEqualTo(1);
        assertThat(reminders("IN_APP", name)).isEqualTo(1);
        assertThat(reminders("EMAIL", name)).isEqualTo(1);
    }

    @Test
    void manualRun_requiresAdminPermission_andOnlyProcessesCallersOrganization() throws Exception {
        String record = recordVaccine("Org-" + suffix(), null, LocalDate.now().plusDays(3));

        postJson(token(ORG_A_CODE, EMAIL_RECEPTION_NOTIF_A1), "/api/v1/notifications/run-vaccine-reminders", Map.of())
                .andExpect(status().isForbidden());
        postJson(token(ORG_A_CODE, EMAIL_VET_A1), "/api/v1/notifications/run-vaccine-reminders", Map.of())
                .andExpect(status().isForbidden());
        postJson(token(ORG_B_CODE, EMAIL_ADMIN_B1), "/api/v1/notifications/run-vaccine-reminders", Map.of())
                .andExpect(status().isOk());
        assertThat(dispatches(record)).isZero();

        postJson(token(ORG_A_CODE, EMAIL_ADMIN_A1), "/api/v1/notifications/run-vaccine-reminders", Map.of()).andExpect(status().isOk());
        assertThat(dispatches(record)).isEqualTo(1);
    }

    // ---- yardımcılar ---------------------------------------------------------------------------------------------------

    private String recordVaccine(String name, OffsetDateTime administeredAt, LocalDate nextDue) throws Exception {
        String vet = token(ORG_A_CODE, EMAIL_VET_A1);
        String encounter = body(postJson(vet, S1, "/api/v1/encounters", Map.of("animalId", PET_A.toString(), "chiefComplaint", "Aşı"))
                .andExpect(status().isCreated())).get("id").asText();
        Map<String, Object> request = new HashMap<>(Map.of("encounterId", encounter, "vaccineName", name, "nextDueAt", nextDue.toString()));
        if (administeredAt != null) {
            request.put("administeredAt", administeredAt.toString());
        }
        return body(postJson(vet, S1, "/api/v1/vaccinations", request).andExpect(status().isCreated())).get("id").asText();
    }

    private static long dispatches(String recordId) {
        return count("SELECT count(*) FROM vet.vaccine_reminder_dispatches WHERE vaccine_record_id = ?::uuid", recordId);
    }

    private static long reminders(String channel, String vaccineName) {
        return count("SELECT count(*) FROM vet.notification_outbox WHERE notification_type = 'VACCINE_DUE' AND channel = ? AND message_body LIKE ?",
                channel, "%" + vaccineName + "%");
    }

    private static String vaccineName(String recordId) {
        return text("SELECT vaccine_name FROM vet.vaccine_records WHERE id = ?::uuid", recordId);
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 6);
    }
}
