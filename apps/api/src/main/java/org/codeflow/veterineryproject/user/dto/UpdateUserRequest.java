package org.codeflow.veterineryproject.user.dto;

import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @Size(max = 200) String displayName,
        @Size(max = 40) String phone
) {
}
