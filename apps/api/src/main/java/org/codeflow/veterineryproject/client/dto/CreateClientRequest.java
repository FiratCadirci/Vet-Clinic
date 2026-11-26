package org.codeflow.veterineryproject.client.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateClientRequest(
        @NotBlank @Pattern(regexp = "PERSON|BUSINESS") String clientType,
        @Size(max = 100) String firstName,
        @Size(max = 100) String lastName,
        @Size(max = 200) String companyName,
        @Size(max = 120) String taxOffice,
        @Size(max = 30) String taxNumber,
        @Pattern(regexp = "SMS|EMAIL|PHONE|WHATSAPP|NONE") String preferredChannel,
        Boolean marketingConsent,
        String communicationNotes,
        @Valid ClientContactRequest contact,
        @Valid ClientAddressRequest address,
        Boolean confirmDuplicate
) {
}
