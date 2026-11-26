package org.codeflow.veterineryproject.encounter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ExaminationRequest(@NotBlank @Size(max = 8000) String examinationSummary) {
}
