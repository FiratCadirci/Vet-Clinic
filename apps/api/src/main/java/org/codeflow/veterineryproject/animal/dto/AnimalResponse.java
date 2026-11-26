package org.codeflow.veterineryproject.animal.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AnimalResponse(
        UUID id,
        String patientNo,
        String name,
        UUID speciesId,
        String speciesName,
        UUID breedId,
        String breedName,
        String sex,
        LocalDate birthDate,
        boolean birthDateEstimated,
        String color,
        String distinguishingMarks,
        boolean sterilized,
        LocalDate sterilizationDate,
        LocalDate deceasedAt,
        String profileNotes,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<AnimalOwnerResponse> owners
) {
    @JsonProperty("primaryOwnerId")
    public UUID primaryOwnerId() {
        if (owners == null) {
            return null;
        }
        return owners.stream().filter(o -> o.primaryContact() && o.validTo() == null)
                .map(AnimalOwnerResponse::clientId).findFirst().orElse(null);
    }

    public AnimalResponse withOwners(List<AnimalOwnerResponse> value) {
        return new AnimalResponse(id, patientNo, name, speciesId, speciesName, breedId, breedName, sex, birthDate,
                birthDateEstimated, color, distinguishingMarks, sterilized, sterilizationDate, deceasedAt, profileNotes,
                createdAt, updatedAt, value);
    }
}
