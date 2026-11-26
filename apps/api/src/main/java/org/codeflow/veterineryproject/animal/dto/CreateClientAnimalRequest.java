package org.codeflow.veterineryproject.animal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Sahip bağlamında hayvan oluşturma; sahip path'ten gelir ve birincil sahip olur. */
public record CreateClientAnimalRequest(
        @NotBlank @Size(max = 150) String name,
        @NotNull UUID speciesId,
        UUID breedId,
        @Pattern(regexp = "MALE|FEMALE|UNKNOWN|INTERSEX") String sex,
        LocalDate birthDate,
        Boolean birthDateEstimated,
        @Size(max = 100) String color,
        String distinguishingMarks,
        Boolean sterilized,
        LocalDate sterilizationDate,
        String profileNotes
) {
    public CreateAnimalRequest toCreateAnimalRequest(UUID clientId) {
        return new CreateAnimalRequest(name, speciesId, breedId, sex, birthDate, birthDateEstimated, color,
                distinguishingMarks, sterilized, sterilizationDate, profileNotes,
                List.of(new AnimalOwnerRequest(clientId, "OWNER", true, true)));
    }
}
