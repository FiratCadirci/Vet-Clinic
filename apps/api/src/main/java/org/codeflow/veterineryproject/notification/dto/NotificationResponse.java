package org.codeflow.veterineryproject.notification.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Gelen kutusundaki uygulama içi bildirim. readAt null ise okunmamıştır. */
public record NotificationResponse(UUID id, String type, String title, String body, UUID animalId, UUID appointmentId,
                                   OffsetDateTime readAt, OffsetDateTime createdAt) {
}
