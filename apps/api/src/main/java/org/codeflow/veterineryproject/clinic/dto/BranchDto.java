package org.codeflow.veterineryproject.clinic.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Şube (vet.clinics) yönetimi DTO'ları. Yanıt alanları GET /api/v1/clinics (ClinicResponse) ile aynıdır. */
public final class BranchDto {

    private BranchDto() {
    }

    public record BranchRequest(
            @NotBlank @Size(min = 2, max = 200) String name,
            @Size(max = 40) String phone,
            @Email @Size(max = 254) String email,
            @Size(max = 100) String city,
            @Size(max = 100) String district,
            @Size(max = 500) String addressLine,
            @Size(max = 20) String postalCode
    ) {
    }

    public record BranchResponse(
            UUID id,
            UUID organizationId,
            UUID clinicGroupId,
            String code,
            String name,
            String phone,
            String email,
            String countryCode,
            String city,
            String district,
            String addressLine,
            String postalCode,
            String timezoneName,
            @JsonProperty("isActive") boolean active,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }
}
