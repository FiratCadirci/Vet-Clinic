package org.codeflow.veterineryproject.animal.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record AnimalOwnerRequest(
        @NotNull UUID clientId,
        @Pattern(regexp = "OWNER|CO_OWNER|GUARDIAN|FOSTER|EMERGENCY_CONTACT|OTHER") String relationshipType,
        Boolean isPrimaryContact,
        Boolean isLegalOwner
) {
}
