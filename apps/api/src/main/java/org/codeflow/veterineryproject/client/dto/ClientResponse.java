package org.codeflow.veterineryproject.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ClientResponse(
        UUID id,
        String clientType,
        String displayName,
        String firstName,
        String lastName,
        String companyName,
        String taxOffice,
        String taxNumber,
        String preferredChannel,
        boolean marketingConsent,
        String communicationNotes,
        boolean active,
        String phone,
        String email,
        ClientAddressResponse address,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<DuplicateClientMatch> possibleDuplicates
) {
    public ClientResponse withPossibleDuplicates(List<DuplicateClientMatch> duplicates) {
        return new ClientResponse(id, clientType, displayName, firstName, lastName, companyName, taxOffice, taxNumber,
                preferredChannel, marketingConsent, communicationNotes, active, phone, email, address,
                createdAt, updatedAt, duplicates);
    }
}
