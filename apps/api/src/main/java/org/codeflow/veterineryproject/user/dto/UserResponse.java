package org.codeflow.veterineryproject.user.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String displayName,
        String phone,
        boolean active,
        boolean mustChangePassword,
        OffsetDateTime lastLoginAt,
        OffsetDateTime createdAt,
        List<ClinicRoleResponse> clinicRoles
) {
    public UserResponse withClinicRoles(List<ClinicRoleResponse> roles) {
        return new UserResponse(id, email, displayName, phone, active, mustChangePassword, lastLoginAt, createdAt, roles);
    }
}
