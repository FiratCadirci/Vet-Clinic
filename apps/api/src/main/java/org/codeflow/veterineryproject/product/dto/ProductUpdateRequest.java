package org.codeflow.veterineryproject.product.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Kısmi güncelleme: null alan değişmez. Barkodu silmek için boş metin ("") gönderilir. */
public record ProductUpdateRequest(
        @Size(max = 80) String sku,
        @Size(max = 100) String barcode,
        @Size(min = 1, max = 250) String name,
        @Pattern(regexp = ProductTypes.PATTERN) String productType,
        @Size(max = 150) String brand,
        @Size(min = 1, max = 30) String baseUnit,
        Boolean stockItem,
        Boolean tracksLot,
        @DecimalMin("0") BigDecimal minimumStock,
        @DecimalMin("0") BigDecimal purchasePrice,
        @DecimalMin("0") BigDecimal salePrice,
        @DecimalMin("0") @DecimalMax("100") BigDecimal taxRate,
        Boolean active
) {
}
