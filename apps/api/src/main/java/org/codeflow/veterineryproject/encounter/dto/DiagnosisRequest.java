package org.codeflow.veterineryproject.encounter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DiagnosisRequest(@NotBlank @Size(max = 8000) String assessment) {
}
