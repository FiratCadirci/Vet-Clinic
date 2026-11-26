package org.codeflow.veterineryproject.notification.model;

import java.util.UUID;

/** Gönderim kaydına PENDING olarak yazılmış ve bir kanala teslim edilecek tek bildirim. */
public record OutgoingNotification(UUID id, UUID organizationId, NotificationType type, NotificationChannelType channel,
                                   NotificationRecipient recipient, String title, String body,
                                   UUID animalId, UUID appointmentId) {
}
