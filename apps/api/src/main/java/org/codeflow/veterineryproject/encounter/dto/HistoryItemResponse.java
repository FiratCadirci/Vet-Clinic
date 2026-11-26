package org.codeflow.veterineryproject.encounter.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Sağlık geçmişi satırı; tıbbi metin içermez, ayrıntı için ilgili kayda gidilir. */
public record HistoryItemResponse(String type, UUID id, OffsetDateTime occurredAt, UUID clinicId, String title,
                                  String detail, UUID encounterId) {
}
