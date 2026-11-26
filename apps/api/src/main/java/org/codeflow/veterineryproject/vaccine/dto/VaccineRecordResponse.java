package org.codeflow.veterineryproject.vaccine.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record VaccineRecordResponse(UUID id, UUID animalId, UUID encounterId, UUID clinicId, UUID protocolDoseId,
                                    String vaccineName, String batchNumber, OffsetDateTime administeredAt,
                                    UUID administeredBy, BigDecimal dose, String doseUnit, String route,
                                    String administrationSite, String certificateNo, String notes, LocalDate nextDueAt,
                                    String nextDueSource, UUID createdBy, OffsetDateTime createdAt) {
}
