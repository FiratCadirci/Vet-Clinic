package org.codeflow.veterineryproject.audit.model;

import java.util.UUID;

/** Kayıt görüntüleme denetim kaydı (FG-J4): yalnız kim/ne/hangi kayıt ve istek bilgisi; içerik yazılmaz. */
public record AccessAuditEntry(UUID organizationId, UUID clinicId, UUID userId, String action, String entityType,
                               UUID entityId, String requestId, String ipAddress, String userAgent) {
}
