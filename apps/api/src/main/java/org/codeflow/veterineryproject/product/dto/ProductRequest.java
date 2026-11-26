package org.codeflow.veterineryproject.product.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Yeni ürün kartı (FG-F1). Organizasyon istekten alınmaz (TenantContext). Fiyatlar KDV hariç TL.
 * {@code sku} boşsa otomatik üretilir; {@code tracksLot} açıksa stok girişinde parti no + SKT zorunludur.
 */
public record ProductRequest(
        @Size(max = 80) String sku,
        @Size(max = 100) String barcode,
        @NotBlank @Size(max = 250) String name,
        @NotNull @Pattern(regexp = ProductTypes.PATTERN) String productType,
        @Size(max = 150) String brand,
        @NotBlank @Size(max = 30) String baseUnit,
        Boolean stockItem,
        Boolean tracksLot,
        @DecimalMin("0") BigDecimal minimumStock,
        @DecimalMin("0") BigDecimal purchasePrice,
        @DecimalMin("0") BigDecimal salePrice,
        @DecimalMin("0") @DecimalMax("100") BigDecimal taxRate
) {
}
