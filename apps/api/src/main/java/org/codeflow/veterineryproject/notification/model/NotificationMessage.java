package org.codeflow.veterineryproject.notification.model;

import java.util.List;
import java.util.UUID;

/**
 * Kanaldan bağımsız bildirim içeriği ve alıcıları.
 *
 * @param storedBody gönderim kaydına yazılacak metin; null ise body. Davet bağlantısı gibi gizli bilgi
 *                   içeren metinler kayda maskelenmiş yazılır, kanala gerçek metin gider.
 */
public record NotificationMessage(NotificationType type, String title, String body, String storedBody,
                                  UUID animalId, UUID appointmentId, List<NotificationRecipient> recipients) {

    public NotificationMessage {
        recipients = List.copyOf(recipients);
    }

    public static NotificationMessage of(NotificationType type, String title, String body, UUID animalId,
                                         UUID appointmentId, List<NotificationRecipient> recipients) {
        return new NotificationMessage(type, title, body, null, animalId, appointmentId, recipients);
    }

    public String bodyToStore() {
        return storedBody != null ? storedBody : body;
    }
}
