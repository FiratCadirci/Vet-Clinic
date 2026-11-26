package org.codeflow.veterineryproject.user.dto;

import java.time.LocalDate;
import java.util.UUID;

public record ClinicRoleResponse(
        UUID id,
        UUID clinicId,
        String clinicName,
        UUID roleId,
        String roleCode,
        String roleName,
        LocalDate validFrom,
        LocalDate validTo
) {
}
