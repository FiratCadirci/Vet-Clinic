package org.codeflow.veterineryproject.user.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record ClinicRoleAssignmentRequest(
        @NotNull UUID clinicId,
        @NotNull UUID roleId,
        LocalDate validFrom,
        LocalDate validTo
) {
}
