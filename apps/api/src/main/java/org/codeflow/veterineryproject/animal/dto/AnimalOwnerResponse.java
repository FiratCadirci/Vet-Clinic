package org.codeflow.veterineryproject.animal.dto;

import java.time.LocalDate;
import java.util.UUID;

public record AnimalOwnerResponse(
        UUID id,
        UUID clientId,
        String clientName,
        String relationshipType,
        boolean primaryContact,
        boolean legalOwner,
        LocalDate validFrom,
        LocalDate validTo
) {
}
