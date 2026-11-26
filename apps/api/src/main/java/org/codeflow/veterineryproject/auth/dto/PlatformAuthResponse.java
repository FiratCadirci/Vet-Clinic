package org.codeflow.veterineryproject.auth.dto;

import java.util.UUID;

public record PlatformAuthResponse(
        String tokenType,
        String accessToken,
        String refreshToken,
        long expiresInSeconds,
        UUID adminId,
        String displayName,
        String email,
        String role
) {
    public PlatformAuthResponse(
            String tokenType,
            String accessToken,
            long expiresInSeconds,
            UUID adminId,
            String displayName,
            String email,
            String role
    ) {
        this(tokenType, accessToken, null, expiresInSeconds, adminId, displayName, email, role);
    }
}

