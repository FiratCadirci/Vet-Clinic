package org.codeflow.veterineryproject.animal.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/** Alanlar null ise değişmez. Tür değiştirilemez. */
public record UpdateAnimalRequest(
        @Size(max = 150) String name,
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
}
