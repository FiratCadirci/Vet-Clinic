package org.codeflow.veterineryproject.encounter.dto;

import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public record UpdateEncounterRequest(
        @Size(max = 4000) String chiefComplaint,
        @Size(max = 8000) String history,
        @Size(max = 8000) String examinationSummary,
        @Size(max = 8000) String assessment,
        @Size(max = 8000) String treatmentPlan,
        @Size(max = 8000) String vetNotes,
        OffsetDateTime followUpAt
) {
}
