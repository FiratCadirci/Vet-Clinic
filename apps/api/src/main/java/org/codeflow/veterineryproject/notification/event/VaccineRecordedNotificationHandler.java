package org.codeflow.veterineryproject.notification.event;

import org.codeflow.veterineryproject.common.event.DomainEvents.VaccineRecordedEvent;
import org.codeflow.veterineryproject.notification.model.NotificationMessage;
import org.codeflow.veterineryproject.notification.model.NotificationType;
import org.codeflow.veterineryproject.notification.repository.NotificationRecipientRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** Aşı kaydedildi → hayvan sahibine, varsa sonraki doz tarihiyle. */
@Component
public class VaccineRecordedNotificationHandler implements NotificationEventHandler<VaccineRecordedEvent> {

    private final NotificationRecipientRepository repository;
    private final OwnerRecipients owners;

    public VaccineRecordedNotificationHandler(NotificationRecipientRepository repository, OwnerRecipients owners) {
        this.repository = repository;
        this.owners = owners;
    }

    @Override
    public Class<VaccineRecordedEvent> eventType() {
        return VaccineRecordedEvent.class;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<NotificationMessage> compose(VaccineRecordedEvent event) {
        OwnerRecipients.Owners to = owners.of(event.animalId());
        if (to.isEmpty()) {
            return Optional.empty();
        }
        String vaccine = repository.findVaccineName(event.vaccineRecordId()).orElse("aşı");
        String body = "%s için %s aşısı kaydedildi.".formatted(to.animalName(), vaccine)
                + (event.nextDueAt() == null ? "" : " Sonraki doz: " + NotificationTexts.date(event.nextDueAt()) + ".");
        return Optional.of(NotificationMessage.of(NotificationType.VACCINE_RECORDED, "Aşı kaydedildi", body,
                event.animalId(), null, to.recipients()));
    }
}
