package org.codeflow.veterineryproject.notification.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PushDeviceResponse(UUID id, String expoPushToken, String platform, OffsetDateTime lastSeenAt) {
}
