package org.codeflow.veterineryproject.encounter.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record TreatmentResponse(
        UUID id, UUID encounterId, String itemType, String name, UUID productId, BigDecimal dose, String doseUnit,
        String route, String frequencyText, Integer durationDays, boolean prescribed, String notes,
        OffsetDateTime performedAt, UUID createdBy, OffsetDateTime createdAt
) {
}
