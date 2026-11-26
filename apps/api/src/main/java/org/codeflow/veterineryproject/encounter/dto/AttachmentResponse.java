package org.codeflow.veterineryproject.encounter.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AttachmentResponse(UUID id, UUID encounterId, UUID clinicId, String documentType, String title,
                                 String originalFilename, String mimeType, Long sizeBytes, UUID uploadedBy,
                                 OffsetDateTime createdAt) {
}
