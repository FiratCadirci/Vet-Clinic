package org.codeflow.veterineryproject.encounter.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record UpdateEncounterRequest(
        @Size(max = 4000) String chiefComplaint,
        @Size(max = 8000) String history,
        @Size(max = 8000) String examinationSummary,
        @Size(max = 8000) String assessment,
        @Size(max = 8000) String treatmentPlan,
        @Size(max = 8000) String vetNotes,
        OffsetDateTime followUpAt,
        @DecimalMin(value = "0", inclusive = false, message = "Kilo sıfırdan büyük olmalıdır") @DecimalMax(value = "2000", message = "Kilo en fazla 2000 kg olabilir") BigDecimal weightKg,
        @DecimalMin(value = "25", message = "Vücut ısısı en az 25 °C olabilir") @DecimalMax(value = "46", message = "Vücut ısısı en fazla 46 °C olabilir") BigDecimal temperatureC
) {
}
