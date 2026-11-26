package org.codeflow.veterineryproject.notification.event;

import org.codeflow.veterineryproject.common.event.DomainEvents.AppointmentStatusChangedEvent;
import org.codeflow.veterineryproject.notification.model.NotificationMessage;
import org.codeflow.veterineryproject.notification.model.NotificationType;
import org.codeflow.veterineryproject.notification.repository.NotificationRecipientRepository;
import org.codeflow.veterineryproject.notification.repository.NotificationRecipientRepository.AppointmentSummary;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** Randevu onaylandı / iptal edildi → hayvan sahibine. Diğer durum geçişleri (kabul, muayene...) bildirim üretmez. */
@Component
public class AppointmentStatusNotificationHandler implements NotificationEventHandler<AppointmentStatusChangedEvent> {

    private final NotificationRecipientRepository repository;
    private final OwnerRecipients owners;

    public AppointmentStatusNotificationHandler(NotificationRecipientRepository repository, OwnerRecipients owners) {
        this.repository = repository;
        this.owners = owners;
    }

    @Override
    public Class<AppointmentStatusChangedEvent> eventType() {
        return AppointmentStatusChangedEvent.class;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<NotificationMessage> compose(AppointmentStatusChangedEvent event) {
        NotificationType type = switch (event.newStatus()) {
            case "CONFIRMED" -> NotificationType.APPOINTMENT_CONFIRMED;
            case "CANCELLED" -> NotificationType.APPOINTMENT_CANCELLED;
            default -> null;
        };
        if (type == null) {
            return Optional.empty();
        }
        Optional<AppointmentSummary> appointment = repository.findAppointment(event.appointmentId());
        if (appointment.isEmpty()) {
            return Optional.empty();
        }
        AppointmentSummary a = appointment.get();
        OwnerRecipients.Owners to = owners.of(a.animalId());
        if (to.isEmpty()) {
            return Optional.empty();
        }
        String when = NotificationTexts.dateTime(a.startsAt());
        String where = a.clinicName() == null ? "" : " (" + a.clinicName() + ")";
        String title = type == NotificationType.APPOINTMENT_CONFIRMED ? "Randevunuz onaylandı" : "Randevunuz iptal edildi";
        String body = type == NotificationType.APPOINTMENT_CONFIRMED
                ? "%s için %s tarihli randevunuz onaylandı%s.".formatted(to.animalName(), when, where)
                : "%s için %s tarihli randevunuz iptal edildi%s. Yeni randevu için kliniğinizle iletişime geçebilirsiniz."
                        .formatted(to.animalName(), when, where);
        return Optional.of(NotificationMessage.of(type, title, body, a.animalId(), event.appointmentId(), to.recipients()));
    }
}
