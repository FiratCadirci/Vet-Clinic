package org.codeflow.veterineryproject.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateVeterinarianRequest(
        @NotBlank @Size(max = 80) String licenseNo,
        @Size(max = 120) String licenseAuthority,
        @Size(max = 120) String specialty,
        @Min(1950) @Max(2100) Integer graduationYear
) {
}
