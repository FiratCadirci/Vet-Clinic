package org.codeflow.veterineryproject.inventory.service;

import org.codeflow.veterineryproject.common.api.ConflictException;

import java.util.Map;
import java.util.UUID;

/** Stok sıfırın altına inecekse (DB guard 23514 veya FEFO'da yeterli geçerli lot yoksa) 409 INSUFFICIENT_STOCK. */
public class InsufficientStockException extends ConflictException {

    public InsufficientStockException(UUID clinicId, UUID productId, String productName) {
        super("Yetersiz stok: %s. Stok sıfırın altına inemez.".formatted(productName), "INSUFFICIENT_STOCK",
                Map.of("clinicId", clinicId, "productId", productId));
    }
}
