package org.codeflow.veterineryproject.animal.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreateAnimalRequest(
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
        String profileNotes,
        @NotEmpty @Valid List<AnimalOwnerRequest> owners
) {
}
