package org.codeflow.veterineryproject.animal.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record DeceasedRequest(@NotNull LocalDate deceasedAt) {
}
