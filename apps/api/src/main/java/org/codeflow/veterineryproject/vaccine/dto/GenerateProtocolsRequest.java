package org.codeflow.veterineryproject.vaccine.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Türün fabrika taslağını (onaysız protokoller) üretme isteği. */
public record GenerateProtocolsRequest(@NotNull(message = "Tür seçilmelidir") UUID speciesId) {
}
