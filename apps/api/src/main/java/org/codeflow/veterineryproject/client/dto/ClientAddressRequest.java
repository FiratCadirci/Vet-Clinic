package org.codeflow.veterineryproject.client.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ClientAddressRequest(
        @NotBlank String addressLine,
        @Size(max = 100) String city,
        @Size(max = 100) String district,
        @Size(max = 120) String neighborhood,
        @Size(max = 20) String postalCode,
        @Pattern(regexp = "[A-Za-z]{2}") String countryCode
) {
}
