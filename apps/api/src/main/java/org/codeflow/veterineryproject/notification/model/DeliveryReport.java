package org.codeflow.veterineryproject.notification.model;

import java.util.UUID;

/** Bir alıcı × kanal denemesinin sonucu (gönderim kaydındaki satırla aynı). */
public record DeliveryReport(UUID deliveryId, NotificationChannelType channel, String recipient, DeliveryStatus status) {
}
