package org.codeflow.veterineryproject.user.dto;

import java.util.UUID;

public record VeterinarianResponse(
        UUID id,
        UUID userId,
        String licenseNo,
        String licenseAuthority,
        String specialty,
        Integer graduationYear,
        boolean active
) {
}
