package org.codeflow.veterineryproject.client.dto;

import java.util.UUID;

public record DuplicateClientMatch(UUID id, String displayName, String phone, String matchedOn, long animalCount) {
}
