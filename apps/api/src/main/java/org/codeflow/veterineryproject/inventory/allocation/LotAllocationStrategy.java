package org.codeflow.veterineryproject.inventory.allocation;

import org.codeflow.veterineryproject.inventory.dto.InventoryDto.LotAllocation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Strategy: çıkış miktarının hangi lotlardan karşılanacağına karar verir. Saf fonksiyondur (DB'siz test edilir).
 * Dönen dağılımın toplamı istenenden azsa stok yetersizdir; kararı çağıran verir.
 */
public interface LotAllocationStrategy {

    List<LotAllocation> allocate(List<StockBucket> buckets, BigDecimal quantity, LocalDate today);
}
