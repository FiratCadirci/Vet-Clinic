package org.codeflow.veterineryproject.animal.dto;

import java.util.UUID;

public record BreedResponse(UUID id, UUID speciesId, String name, boolean mixed) {
}
