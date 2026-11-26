package org.codeflow.veterineryproject.client.dto;

import java.util.UUID;

/** Müşteri listesindeki hayvan özeti; isPrimaryOwner, bu müşterinin hayvanın birincil sahibi olup olmadığını belirtir. */
public record ClientAnimalSummary(
        UUID id,
        String name,
        UUID speciesId,
        String speciesName,
        boolean deceased,
        boolean isPrimaryOwner
) {
}
