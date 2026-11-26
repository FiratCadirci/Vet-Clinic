package org.codeflow.veterineryproject.product.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Ürün kartı. {@code productType} şemadaki değerlerden biridir (bkz. {@link ProductTypes}). */
public record ProductResponse(
        UUID id,
        String sku,
        String barcode,
        String name,
        String productType,
        String brand,
        String baseUnit,
        boolean stockItem,
        boolean tracksLot,
        BigDecimal minimumStock,
        BigDecimal purchasePrice,
        BigDecimal salePrice,
        BigDecimal taxRate,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
