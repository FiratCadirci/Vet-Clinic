package org.codeflow.veterineryproject.auth.dto;

import java.util.UUID;

public record AuthLoginCandidate(
        UUID organizationId,
        int organizationCode,
        String organizationName,
        UUID userId,
        boolean client
) {
}
