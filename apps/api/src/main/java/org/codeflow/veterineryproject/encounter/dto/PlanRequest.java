package org.codeflow.veterineryproject.encounter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public record PlanRequest(@NotBlank @Size(max = 8000) String treatmentPlan, OffsetDateTime followUpAt) {
}
