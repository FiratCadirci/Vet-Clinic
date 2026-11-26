package org.codeflow.veterineryproject.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record PlatformRefreshRequest(@NotBlank String refreshToken) {
}
