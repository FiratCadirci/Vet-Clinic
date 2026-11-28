package org.codeflow.veterineryproject.vaccine.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Protokol kalemlerinin değiştirilmesi: yeni taslak sürüm oluşur, hekim onayı sıfırlanır. */
public record UpdateProtocolRequest(
        @NotBlank @Size(max = 200) String name,
        @Min(1) Integer boosterIntervalDays,
        @Size(max = 2000) String notes,
        @NotEmpty @Valid List<ProtocolDoseRequest> doses
) {
}
