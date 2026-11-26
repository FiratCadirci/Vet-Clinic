package org.codeflow.veterineryproject.vaccine.dto;

import java.util.UUID;

public record ProtocolDoseResponse(UUID id, int doseNumber, String vaccineName, Integer recommendedAgeDays,
                                   Integer intervalFromPreviousDays, String notes) {
}
