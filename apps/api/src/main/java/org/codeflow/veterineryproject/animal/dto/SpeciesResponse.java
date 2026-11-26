package org.codeflow.veterineryproject.animal.dto;

import java.util.UUID;

public record SpeciesResponse(UUID id, String code, String name, String scientificName) {
}
