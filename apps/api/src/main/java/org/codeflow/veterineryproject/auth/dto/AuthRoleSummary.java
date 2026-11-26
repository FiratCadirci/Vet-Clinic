package org.codeflow.veterineryproject.auth.dto;

import java.util.UUID;

public record AuthRoleSummary(UUID id, String code, String name) {
}
