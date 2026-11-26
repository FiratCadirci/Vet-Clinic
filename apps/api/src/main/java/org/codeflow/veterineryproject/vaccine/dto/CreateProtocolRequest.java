package org.codeflow.veterineryproject.vaccine.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateProtocolRequest(
        @NotBlank @Size(max = 60) String code,
        @NotBlank @Size(max = 200) String name,
        @NotNull UUID speciesId,
        @Min(1) Integer boosterIntervalDays,
        @Size(max = 2000) String notes,
        @NotEmpty @Valid List<ProtocolDoseRequest> doses
) {
}
