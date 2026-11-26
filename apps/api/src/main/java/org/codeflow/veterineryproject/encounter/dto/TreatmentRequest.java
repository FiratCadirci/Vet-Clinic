package org.codeflow.veterineryproject.encounter.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record TreatmentRequest(
        @NotNull @Pattern(regexp = "PROCEDURE|MEDICATION") String itemType,
        @NotBlank @Size(max = 200) String name,
        UUID productId,
        @DecimalMin(value = "0", inclusive = false) BigDecimal dose,
        @Size(max = 40) String doseUnit,
        @Size(max = 80) String route,
        @Size(max = 150) String frequencyText,
        @Min(1) Integer durationDays,
        Boolean prescribed,
        @Size(max = 4000) String notes,
        OffsetDateTime performedAt
) {
}
