package org.codeflow.veterineryproject.billing.service;

import org.codeflow.veterineryproject.inventory.dto.InventoryDto.LotAllocation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Fatura satırlarının FEFO lot parçalarına bölünmesi (DB'siz). */
class InvoiceLotAllocatorTest {

    private static LotAllocation lot(String no, String qty) {
        return new LotAllocation(UUID.randomUUID(), no, LocalDate.of(2027, 1, 1), new BigDecimal(qty));
    }

    @Test
    void tailSkipsQuantityAlreadyGivenToEarlierLinesOfSameProduct() {
        List<LotAllocation> plan = List.of(lot("A", "2"), lot("B", "3"), lot("C", "4"));

        List<LotAllocation> rest = InvoiceLotAllocator.tail(plan, new BigDecimal("3"));

        assertThat(rest).extracting(LotAllocation::lotNumber).containsExactly("B", "C");
        assertThat(rest.get(0).quantity()).isEqualByComparingTo("2");
        assertThat(rest.get(1).quantity()).isEqualByComparingTo("4");
        assertThat(InvoiceLotAllocator.tail(plan, BigDecimal.ZERO)).isEqualTo(plan);
    }

    @Test
    void discountIsSplitByQuantityAndRemainderGoesToLastPart() {
        List<LotAllocation> plan = List.of(lot("A", "1"), lot("B", "2"));

        List<BigDecimal> parts = InvoiceLotAllocator.splitDiscount(new BigDecimal("10.00"), new BigDecimal("3"), plan);

        assertThat(parts.get(0)).isEqualByComparingTo("3.33");
        assertThat(parts.get(1)).isEqualByComparingTo("6.67");
        assertThat(parts.stream().reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("10.00");
    }
}
