package org.codeflow.veterineryproject.encounter.dto;

import jakarta.validation.constraints.Size;

public record CloseEncounterRequest(@Size(max = 8000) String vetNotes) {
}
