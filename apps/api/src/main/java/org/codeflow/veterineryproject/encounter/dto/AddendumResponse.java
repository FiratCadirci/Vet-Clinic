package org.codeflow.veterineryproject.encounter.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AddendumResponse(UUID id, UUID encounterId, UUID clinicId, String note, UUID createdBy, OffsetDateTime createdAt) {
}
