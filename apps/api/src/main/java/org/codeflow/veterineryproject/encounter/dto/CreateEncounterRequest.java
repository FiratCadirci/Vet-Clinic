package org.codeflow.veterineryproject.encounter.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateEncounterRequest(
        @NotNull UUID animalId,
        @Pattern(regexp = "CONSULTATION|EMERGENCY|VACCINATION|SURGERY|FOLLOW_UP|TELEMEDICINE|OTHER") String encounterType,
        @Size(max = 4000) String chiefComplaint,
        @Size(max = 8000) String history
) {
}
