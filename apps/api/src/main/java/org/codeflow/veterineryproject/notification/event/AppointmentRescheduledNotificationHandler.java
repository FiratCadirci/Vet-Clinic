package org.codeflow.veterineryproject.notification.event;

import org.codeflow.veterineryproject.common.event.DomainEvents.AppointmentRescheduledEvent;
import org.codeflow.veterineryproject.notification.model.NotificationMessage;
import org.codeflow.veterineryproject.notification.model.NotificationType;
import org.codeflow.veterineryproject.notification.repository.NotificationRecipientRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** Randevu ertelendi (zamanı değişti) → hayvan sahibine yeni tarih. */
@Component
public class AppointmentRescheduledNotificationHandler implements NotificationEventHandler<AppointmentRescheduledEvent> {

    private final NotificationRecipientRepository repository;
    private final OwnerRecipients owners;

    public AppointmentRescheduledNotificationHandler(NotificationRecipientRepository repository, OwnerRecipients owners) {
        this.repository = repository;
        this.owners = owners;
    }

    @Override
    public Class<AppointmentRescheduledEvent> eventType() {
        return AppointmentRescheduledEvent.class;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<NotificationMessage> compose(AppointmentRescheduledEvent event) {
        return repository.findAppointment(event.appointmentId()).flatMap(a -> {
            OwnerRecipients.Owners to = owners.of(a.animalId());
            if (to.isEmpty()) {
                return Optional.empty();
            }
            String where = a.clinicName() == null ? "" : " (" + a.clinicName() + ")";
            String body = "%s için randevunuzun yeni tarihi: %s%s.".formatted(to.animalName(),
                    NotificationTexts.dateTime(a.startsAt()), where);
            return Optional.of(NotificationMessage.of(NotificationType.APPOINTMENT_RESCHEDULED, "Randevunuz ertelendi",
                    body, a.animalId(), event.appointmentId(), to.recipients()));
        });
    }
}
