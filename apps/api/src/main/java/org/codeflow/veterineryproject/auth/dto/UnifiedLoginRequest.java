package org.codeflow.veterineryproject.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UnifiedLoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password,
        Integer organizationCode
) {
}
