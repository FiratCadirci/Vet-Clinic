package org.codeflow.veterineryproject.auth.dto;

import java.util.UUID;

public record AuthClinicSummary(UUID id, String code, String name) {
}
