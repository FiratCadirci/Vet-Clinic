package org.codeflow.veterineryproject.notification.service;

import org.codeflow.veterineryproject.common.tenant.TenantContext;
import org.codeflow.veterineryproject.notification.dto.ReminderRunResponse;
import org.codeflow.veterineryproject.notification.model.NotificationMessage;
import org.codeflow.veterineryproject.notification.model.NotificationRecipient;
import org.codeflow.veterineryproject.notification.model.NotificationType;
import org.codeflow.veterineryproject.notification.repository.NotificationRecipientRepository;
import org.codeflow.veterineryproject.notification.repository.NotificationRecipientRepository.OwnerContact;
import org.codeflow.veterineryproject.notification.repository.VaccineReminderRepository;
import org.codeflow.veterineryproject.notification.repository.VaccineReminderRepository.ReminderCandidate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Aşı hatırlatma görevi (FG-D5): her gün 09:00 (Europe/Istanbul) tüm organizasyonları dolaşır; 3 gün içinde
 * vadesi gelen ya da geciken aşılar için sahibe uygulama içi + e-posta + push hatırlatma gönderir.
 * <p>
 * Tekillik: hatırlatma göndermeden önce (kayıt, tür) satırı vaccine_reminder_dispatches'e yazılarak
 * "sahiplenilir"; birincil anahtar zamanlanmış görev ile elle tetikleme çakışsa bile tek bildirim garantiler.
 * RLS delinmez: her organizasyon kendi tenant bağlamında (kullanıcısız) işlenir; aday ve alıcı listeleri
 * app.organization_id ile sınırlı SECURITY DEFINER fonksiyonlardan gelir. Denetim izi kullanıcı istediği
 * için burada yazılmaz.
 */
@Component
public class VaccineReminderJob {

    private static final Logger log = LoggerFactory.getLogger(VaccineReminderJob.class);
    static final int DAYS_AHEAD = 3;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("tr-TR"));

    private final VaccineReminderRepository reminders;
    private final NotificationRecipientRepository recipients;
    private final NotificationService notifications;
    private final TransactionTemplate readTx;
    private final TransactionTemplate writeTx;

    public VaccineReminderJob(VaccineReminderRepository reminders, NotificationRecipientRepository recipients,
                              NotificationService notifications, PlatformTransactionManager transactionManager) {
        this.reminders = reminders;
        this.recipients = recipients;
        this.notifications = notifications;
        this.readTx = new TransactionTemplate(transactionManager);
        this.readTx.setReadOnly(true);
        this.writeTx = new TransactionTemplate(transactionManager);
    }

    @Scheduled(cron = "${app.notifications.vaccine-reminder-cron:0 0 9 * * *}", zone = "Europe/Istanbul")
    public void runDaily() {
        int total = 0;
        for (UUID organizationId : reminders.findActiveOrganizations()) {
            try (TenantContext.Scope ignored = TenantContext.withTenant(organizationId, null, null)) {
                total += runForOrganization(organizationId);
            } catch (RuntimeException ex) {
                log.error("Aşı hatırlatmaları işlenemedi organizationId={}: {}", organizationId, ex.getMessage(), ex);
            }
        }
        log.info("Aşı hatırlatma görevi tamamlandı: {} hatırlatma", total);
    }

    /** Demo / elle tetikleme: yalnız çağıranın organizasyonu. */
    public ReminderRunResponse runForCurrentOrganization() {
        return new ReminderRunResponse(runForOrganization(TenantContext.require().organizationId()));
    }

    private int runForOrganization(UUID organizationId) {
        List<ReminderCandidate> candidates = readTx.execute(s -> reminders.findCandidates(DAYS_AHEAD));
        int processed = 0;
        for (ReminderCandidate candidate : candidates == null ? List.<ReminderCandidate>of() : candidates) {
            // Sahibi olmayan kayıt sahiplenilmez; sahip sonradan eklenirse hatırlatma yine gider.
            Optional<NotificationMessage> message = readTx.execute(s -> compose(candidate));
            if (message == null || message.isEmpty()) {
                continue;
            }
            Boolean claimed = writeTx.execute(s -> reminders.claim(organizationId, candidate.vaccineRecordId(),
                    candidate.kind(), candidate.dueDate()));
            if (Boolean.TRUE.equals(claimed)) {
                notifications.send(message.get());
                processed++;
            }
        }
        return processed;
    }

    private Optional<NotificationMessage> compose(ReminderCandidate c) {
        List<OwnerContact> owners = recipients.findAnimalOwners(c.animalId());
        if (owners.isEmpty()) {
            return Optional.empty();
        }
        String animal = owners.get(0).animalName();
        String date = DATE.format(c.dueDate());
        String title = c.overdue() ? "Aşı tarihi geçti" : "Aşı zamanı yaklaşıyor";
        String body = c.overdue()
                ? "%s için %s aşısının tarihi (%s) geçti. Lütfen kliniğinizden randevu alın.".formatted(animal, c.vaccineName(), date)
                : "%s için %s aşısı %s tarihinde yapılmalı. Randevu almayı unutmayın.".formatted(animal, c.vaccineName(), date);
        List<NotificationRecipient> to = owners.stream().map(OwnerContact::toRecipient).toList();
        return Optional.of(NotificationMessage.of(NotificationType.VACCINE_DUE, title, body, c.animalId(), null, to));
    }
}
