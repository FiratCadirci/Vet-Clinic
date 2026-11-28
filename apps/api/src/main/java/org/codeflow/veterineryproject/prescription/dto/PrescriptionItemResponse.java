package org.codeflow.veterineryproject.prescription.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PrescriptionItemResponse(UUID id, UUID prescriptionId, String prescriptionNo, UUID encounterId, int lineNo,
                                       UUID productId, String productName, BigDecimal dose, String doseUnit, String route,
                                       String frequencyText, Integer durationDays, BigDecimal quantity,
                                       String instructions) {
}
