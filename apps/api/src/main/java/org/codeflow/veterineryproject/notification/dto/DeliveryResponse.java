package org.codeflow.veterineryproject.notification.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Gönderim kaydı satırı (FG-I3): hangi olay, hangi kanal, kime, sonuç ve hata.
 *
 * @param recipientEmail yalnız EMAIL kanalında dolu; IN_APP/PUSH için alıcı userId'dir
 */
public record DeliveryResponse(UUID id, OffsetDateTime createdAt, OffsetDateTime sentAt, String type, String channel,
                               String status, UUID userId, UUID clientId, String recipientName, String recipientEmail,
                               String title, int attemptCount, String error) {
}
