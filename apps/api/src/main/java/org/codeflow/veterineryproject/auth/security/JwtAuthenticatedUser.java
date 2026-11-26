package org.codeflow.veterineryproject.auth.security;

import java.util.List;
import java.util.UUID;

public record JwtAuthenticatedUser(
        UUID userId,
        UUID organizationId,
        UUID clinicId,
        List<UUID> clinicIds,
        List<String> permissions,
        String tokenId,
        java.time.Instant tokenExpiresAt
) {
    public JwtAuthenticatedUser(UUID userId, UUID organizationId, UUID clinicId, List<UUID> clinicIds, List<String> permissions) {
        this(userId, organizationId, clinicId, clinicIds, permissions, null, null);
    }
}

