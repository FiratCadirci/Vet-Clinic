package org.codeflow.veterineryproject.user.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateUserRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 200) String displayName,
        @Size(max = 40) String phone,
        @NotBlank @Size(min = 10, max = 100) String initialPassword,
        @NotEmpty @Valid List<ClinicRoleAssignmentRequest> assignments
) {
}
