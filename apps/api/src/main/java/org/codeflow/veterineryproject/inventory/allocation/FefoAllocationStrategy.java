package org.codeflow.veterineryproject.inventory.allocation;

import org.codeflow.veterineryproject.inventory.dto.InventoryDto.LotAllocation;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * FEFO (first expired, first out, FG-F4): son kullanma tarihi en yakın lottan başlar. Süresi geçmiş lotlar
 * ve elde miktarı olmayanlar atlanır; SKT'siz (ve lotsuz) stok en sona kalır. Eşit SKT'de önce giren önce çıkar.
 */
@Component
public class FefoAllocationStrategy implements LotAllocationStrategy {

    private static final Comparator<StockBucket> FEFO = Comparator
            .comparing(StockBucket::expiresAt, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(StockBucket::receivedAt, Comparator.nullsLast(Comparator.<OffsetDateTime>naturalOrder()))
            .thenComparing(StockBucket::lotNumber, Comparator.nullsLast(Comparator.naturalOrder()));

    @Override
    public List<LotAllocation> allocate(List<StockBucket> buckets, BigDecimal quantity, LocalDate today) {
        List<LotAllocation> result = new ArrayList<>();
        BigDecimal left = quantity;
        List<StockBucket> usable = buckets.stream()
                .filter(b -> b.available().signum() > 0)
                .filter(b -> b.expiresAt() == null || !b.expiresAt().isBefore(today))
                .sorted(FEFO)
                .toList();
        for (StockBucket b : usable) {
            if (left.signum() <= 0) {
                break;
            }
            BigDecimal take = b.available().min(left);
            result.add(new LotAllocation(b.lotId(), b.lotNumber(), b.expiresAt(), take));
            left = left.subtract(take);
        }
        return result;
    }
}
