package org.codeflow.veterineryproject.common.audit;

import java.util.Map;
import java.util.UUID;

public record AuditLogCreateRequest(
        UUID organizationId,
        UUID clinicId,
        UUID actorUserId,
        String action,
        String entityType,
        UUID entityId,
        String requestId,
        String ipAddress,
        String userAgent,
        Map<String, Object> oldValues,
        Map<String, Object> newValues,
        String reason
) {
}

