package org.codeflow.veterineryproject.auth.dto;

import java.util.List;
import java.util.UUID;

public record AuthLoginResponse(
        String tokenType,
        String accessToken,
        String refreshToken,
        long expiresInSeconds,
        UUID organizationId,
        UUID userId,
        String displayName,
        List<UUID> clinicIds,
        List<String> permissions,
        boolean mustChangePassword,
        List<AuthRoleSummary> roles,
        List<AuthClinicSummary> clinics
) {
    public AuthLoginResponse(
            String tokenType,
            String accessToken,
            String refreshToken,
            long expiresInSeconds,
            UUID organizationId,
            UUID userId,
            String displayName,
            List<UUID> clinicIds,
            List<String> permissions,
            boolean mustChangePassword
    ) {
        this(tokenType, accessToken, refreshToken, expiresInSeconds, organizationId, userId, displayName,
                clinicIds, permissions, mustChangePassword, List.of(), List.of());
    }

    public AuthLoginResponse(
            String tokenType,
            String accessToken,
            String refreshToken,
            long expiresInSeconds,
            UUID organizationId,
            UUID userId,
            String displayName,
            List<UUID> clinicIds,
            List<String> permissions
    ) {
        this(tokenType, accessToken, refreshToken, expiresInSeconds, organizationId, userId, displayName, clinicIds, permissions, false);
    }
}

