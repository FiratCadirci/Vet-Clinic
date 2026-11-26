package org.codeflow.veterineryproject.encounter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddendumRequest(@NotBlank @Size(max = 8000) String note) {
}
