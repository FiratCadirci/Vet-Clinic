package org.codeflow.veterineryproject.vaccine.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProtocolDoseRequest(
        @NotNull @Min(1) Integer doseNumber,
        @NotBlank @Size(max = 200) String vaccineName,
        @Min(0) Integer recommendedAgeDays,
        @Min(1) Integer intervalFromPreviousDays,
        @Size(max = 2000) String notes
) {
}
