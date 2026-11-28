package org.codeflow.veterineryproject.audit.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Denetim izi satırı: kim (kullanıcı adıyla), ne, hangi kayıt, hangi şube, ne zaman. Eski/yeni değerler dönmez. */
public record AuditLogResponse(long id, OffsetDateTime occurredAt, UUID actorUserId, String userName, String action,
                               String entityType, UUID entityId, UUID clinicId, String clinicName, String ipAddress) {
}
