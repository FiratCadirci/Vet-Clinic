package org.codeflow.veterineryproject.encounter.dto;

import jakarta.validation.constraints.Size;

public record StartFromAppointmentRequest(
        @Size(max = 4000) String chiefComplaint,
        @Size(max = 8000) String history
) {
}
