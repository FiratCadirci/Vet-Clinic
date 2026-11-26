package org.codeflow.veterineryproject.client.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record ClientContactRequest(
        @Size(max = 40) String phone,
        @Email @Size(max = 254) String email
) {
}
