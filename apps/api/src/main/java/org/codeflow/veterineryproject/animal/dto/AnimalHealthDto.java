package org.codeflow.veterineryproject.animal.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class AnimalHealthDto {

    private AnimalHealthDto() {
    }

    public record WeightRequest(
            @NotNull @DecimalMin(value = "0.001") @DecimalMax(value = "1999.999") BigDecimal weightKg,
            OffsetDateTime measuredAt,
            @Pattern(regexp = "CLINIC|OWNER|IMPORTED|OTHER") String measurementSource,
            @Size(max = 1000) String note) {
    }

    public record WeightResponse(UUID id, BigDecimal weightKg, OffsetDateTime measuredAt, String measurementSource,
                                 String note, UUID recordedBy) {
    }

    public record AllergyRequest(
            @NotBlank @Size(max = 200) String substance,
            @Size(max = 1000) String reaction,
            @Pattern(regexp = "MILD|MODERATE|SEVERE|UNKNOWN") String severity,
            LocalDate onsetDate) {
    }

    public record AllergyResponse(UUID id, String substance, String reaction, String severity, LocalDate onsetDate,
                                  LocalDate resolvedAt, boolean active) {
    }

    public record IdentifierRequest(
            @NotNull @Pattern(regexp = "MICROCHIP|PASSPORT|TATTOO|REGISTRY|OTHER") String identifierType,
            @NotBlank @Size(max = 150) String identifierValue,
            LocalDate issuedAt,
            LocalDate expiresAt,
            @Size(max = 150) String issuingAuthority) {
    }

    public record IdentifierResponse(UUID id, String identifierType, String identifierValue, LocalDate issuedAt,
                                     LocalDate expiresAt, String issuingAuthority, boolean verified) {
    }

    /** Hasta kartı başlığı: son kilo, aktif alerjiler ve kimlik numaraları. */
    public record HealthSummary(WeightResponse latestWeight, List<AllergyResponse> activeAllergies,
                                List<IdentifierResponse> identifiers) {
    }
}
