package org.codeflow.veterineryproject.prescription.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/** Reçete kalemi: ilaç ürün kartından seçilir; frequencyText kullanım şeklidir (ör. "12 saatte bir 1 tablet"). */
public record PrescriptionItemRequest(
        @NotNull(message = "İlaç seçilmelidir") UUID productId,
        @DecimalMin(value = "0", inclusive = false, message = "Doz sıfırdan büyük olmalıdır") BigDecimal dose,
        @Size(max = 40) String doseUnit,
        @Size(max = 80) String route,
        @NotBlank(message = "Kullanım şekli (doz/sıklık) zorunludur") @Size(max = 150) String frequencyText,
        @Min(value = 1, message = "Süre en az 1 gün olmalıdır") Integer durationDays,
        @DecimalMin(value = "0", inclusive = false, message = "Miktar sıfırdan büyük olmalıdır") BigDecimal quantity,
        @Size(max = 4000) String instructions
) {
}
