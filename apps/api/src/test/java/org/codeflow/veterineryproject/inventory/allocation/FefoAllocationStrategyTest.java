package org.codeflow.veterineryproject.inventory.allocation;

import org.codeflow.veterineryproject.inventory.dto.InventoryDto.LotAllocation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** FEFO stratejisi (FG-F4): DB'siz birim testleri. */
class FefoAllocationStrategyTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 15);
    private final LotAllocationStrategy fefo = new FefoAllocationStrategy();

    @Test
    void takesEarliestExpiryFirstAndSplitsAcrossLots() {
        List<StockBucket> buckets = List.of(
                lot("LATE", TODAY.plusDays(300), 5, 1),
                lot("SOON", TODAY.plusDays(10), 3, 2),
                lot("MID", TODAY.plusDays(60), 2, 3));

        List<LotAllocation> plan = fefo.allocate(buckets, new BigDecimal("6"), TODAY);

        assertThat(plan).extracting(LotAllocation::lotNumber).containsExactly("SOON", "MID", "LATE");
        assertThat(plan).extracting(LotAllocation::quantity).usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("3"), new BigDecimal("2"), new BigDecimal("1"));
    }

    @Test
    void skipsExpiredAndEmptyLotsButAcceptsLotExpiringToday() {
        List<StockBucket> buckets = List.of(
                lot("EXPIRED", TODAY.minusDays(1), 10, 1),
                lot("EMPTY", TODAY.plusDays(1), 0, 2),
                lot("TODAY", TODAY, 1, 3),
                lot("NEXT", TODAY.plusDays(5), 4, 4));

        List<LotAllocation> plan = fefo.allocate(buckets, new BigDecimal("3"), TODAY);

        assertThat(plan).extracting(LotAllocation::lotNumber).containsExactly("TODAY", "NEXT");
    }

    @Test
    void lotlessAndUndatedStockComesLast() {
        StockBucket lotless = new StockBucket(null, null, null, null, new BigDecimal("10"));
        List<StockBucket> buckets = List.of(lotless, lot("UNDATED", null, 1, 1), lot("DATED", TODAY.plusDays(30), 1, 2));

        List<LotAllocation> plan = fefo.allocate(buckets, new BigDecimal("4"), TODAY);

        assertThat(plan).extracting(LotAllocation::lotNumber).containsExactly("DATED", "UNDATED", null);
        assertThat(plan.get(2).lotId()).isNull();
        assertThat(plan.get(2).quantity()).isEqualByComparingTo("2");
    }

    @Test
    void sameExpiryUsesFirstReceivedFirst() {
        LocalDate exp = TODAY.plusDays(30);
        List<StockBucket> buckets = List.of(lot("B", exp, 2, 5), lot("A", exp, 2, 9), lot("C", exp, 2, 1));

        List<LotAllocation> plan = fefo.allocate(buckets, new BigDecimal("2"), TODAY);

        assertThat(plan).extracting(LotAllocation::lotNumber).containsExactly("C");
    }

    @Test
    void returnsPartialPlanWhenStockIsInsufficient() {
        List<StockBucket> buckets = List.of(lot("ONLY", TODAY.plusDays(30), 2, 1), lot("OLD", TODAY.minusDays(3), 5, 2));

        List<LotAllocation> plan = fefo.allocate(buckets, new BigDecimal("5"), TODAY);

        BigDecimal allocated = plan.stream().map(LotAllocation::quantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(allocated).isEqualByComparingTo("2");
    }

    private static StockBucket lot(String number, LocalDate expires, int qty, int receivedDay) {
        return new StockBucket(UUID.randomUUID(), number, expires,
                OffsetDateTime.of(2026, 1, receivedDay, 9, 0, 0, 0, ZoneOffset.UTC), new BigDecimal(qty));
    }
}
