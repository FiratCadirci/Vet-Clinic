package org.codeflow.veterineryproject.client.dto;

public record ClientAddressResponse(
        String addressLine,
        String city,
        String district,
        String neighborhood,
        String postalCode,
        String countryCode
) {
}
