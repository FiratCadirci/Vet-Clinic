package org.codeflow.veterineryproject.inventory.allocation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Bir lokasyondaki tek bir lotun (lotId = null: lotsuz stok) eldeki miktarı. */
public record StockBucket(UUID lotId, String lotNumber, LocalDate expiresAt, OffsetDateTime receivedAt,
                          BigDecimal available) {
}
