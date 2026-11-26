package org.codeflow.veterineryproject.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record AuthRefreshRequest(
        @NotBlank String refreshToken
) {
}

