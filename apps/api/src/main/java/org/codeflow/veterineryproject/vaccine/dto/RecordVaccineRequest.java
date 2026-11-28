package org.codeflow.veterineryproject.vaccine.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RecordVaccineRequest(
        @NotNull UUID encounterId,
        UUID protocolDoseId,
        @Size(max = 200) String vaccineName,
        @Size(max = 100) String batchNumber,
        OffsetDateTime administeredAt,
        @DecimalMin(value = "0", inclusive = false) BigDecimal dose,
        @Size(max = 40) String doseUnit,
        @Size(max = 80) String route,
        @Size(max = 120) String administrationSite,
        @Size(max = 100) String certificateNo,
        @Size(max = 2000) String notes,
        LocalDate nextDueAt,
        /** Seçilirse şube stoğundan 1 birim FEFO ile düşülür; lot takipli üründe kayda düşülen lot yazılır. */
        UUID productId
) {
}
