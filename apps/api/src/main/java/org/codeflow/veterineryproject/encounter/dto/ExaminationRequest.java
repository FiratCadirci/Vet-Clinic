package org.codeflow.veterineryproject.encounter.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Zorunlu alanlar muayene türünün akışına göre denetlenir (ör. aşı ziyaretinde kilo ve ısı). */
public record ExaminationRequest(
        @Size(max = 8000) String examinationSummary,
        @DecimalMin(value = "0", inclusive = false, message = "Kilo sıfırdan büyük olmalıdır") @DecimalMax(value = "2000", message = "Kilo en fazla 2000 kg olabilir") BigDecimal weightKg,
        @DecimalMin(value = "25", message = "Vücut ısısı en az 25 °C olabilir") @DecimalMax(value = "46", message = "Vücut ısısı en fazla 46 °C olabilir") BigDecimal temperatureC
) {
}
