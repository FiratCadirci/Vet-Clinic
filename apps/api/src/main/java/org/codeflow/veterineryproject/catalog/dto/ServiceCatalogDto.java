package org.codeflow.veterineryproject.catalog.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/** Klinik hizmetleri kataloğu (vet.service_catalog): randevu süresi ve satış kalemi fiyatı. Fiyatlar KDV hariçtir. */
public final class ServiceCatalogDto {

    private ServiceCatalogDto() {
    }

    /** Kod verilmezse addan üretilir; organizasyon içinde tekildir. */
    public record ServiceRequest(
            @Size(max = 80) String code,
            @NotBlank @Size(min = 2, max = 200) String name,
            @Size(max = 80) String category,
            @NotNull @Min(5) @Max(480) Integer durationMinutes,
            @NotNull @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal price,
            @NotNull @DecimalMin("0.00") @DecimalMax("100.00") @Digits(integer = 3, fraction = 2) BigDecimal taxRate,
            Boolean active
    ) {
    }

    public record ServiceResponse(UUID id, String code, String name, String category, Integer durationMinutes,
                                  BigDecimal price, BigDecimal taxRate, boolean active) {
    }
}
