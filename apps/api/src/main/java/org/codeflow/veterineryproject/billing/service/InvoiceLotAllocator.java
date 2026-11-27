package org.codeflow.veterineryproject.billing.service;

import org.codeflow.veterineryproject.billing.repository.BillingJdbcRepository;
import org.codeflow.veterineryproject.billing.repository.BillingJdbcRepository.StockLine;
import org.codeflow.veterineryproject.inventory.dto.InventoryDto.LotAllocation;
import org.codeflow.veterineryproject.inventory.service.StockConsumptionService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Kesimden hemen önce (aynı transaction'da, fatura hâlâ DRAFT iken) lotu seçilmemiş stok ürünü satırlarını
 * FEFO dağıtımına göre lot satırlarına böler. Fatura tetikleyicisi lot takipli üründe satırda lot ister ve stok
 * çıkışını satırlardan yazar; dağıtımın kendisi stok modülünündür (StockConsumptionService.allocateFefo).
 * Yetersiz stokta InsufficientStockException (409) fırlar; transaction geri alınır, satırlar değişmez.
 */
@Component
class InvoiceLotAllocator {

    private final BillingJdbcRepository repository;
    private final StockConsumptionService stock;

    InvoiceLotAllocator(BillingJdbcRepository repository, StockConsumptionService stock) {
        this.repository = repository;
        this.stock = stock;
    }

    void allocate(UUID invoiceId, UUID clinicId) {
        // Aynı ürünün birden çok satırı aynı lotu iki kez saymasın: ürün başına önceki satırların miktarı atlanır.
        Map<UUID, BigDecimal> consumed = new HashMap<>();
        for (StockLine line : repository.unallocatedStockLines(invoiceId)) {
            BigDecimal before = consumed.getOrDefault(line.productId(), BigDecimal.ZERO);
            List<LotAllocation> plan = tail(stock.allocateFefo(clinicId, line.productId(),
                    before.add(line.quantity())), before);
            consumed.put(line.productId(), before.add(line.quantity()));
            if (plan.isEmpty() || (plan.size() == 1 && plan.get(0).lotId() == null)) {
                continue;
            }
            List<BigDecimal> discounts = splitDiscount(line.discountAmount(), line.quantity(), plan);
            for (int i = 0; i < plan.size(); i++) {
                LotAllocation part = plan.get(i);
                if (i == 0) {
                    repository.allocateLine(line.id(), part.quantity(), discounts.get(i), part.lotId());
                } else {
                    repository.insertSplitLine(line.id(), part.quantity(), discounts.get(i), part.lotId());
                }
            }
        }
    }

    /** Dağıtımın ilk {@code skip} miktarını atlar (önceki satırlara ayrılan kısım). */
    static List<LotAllocation> tail(List<LotAllocation> allocations, BigDecimal skip) {
        List<LotAllocation> result = new ArrayList<>();
        BigDecimal toSkip = skip;
        for (LotAllocation a : allocations) {
            if (toSkip.signum() > 0) {
                if (a.quantity().compareTo(toSkip) <= 0) {
                    toSkip = toSkip.subtract(a.quantity());
                    continue;
                }
                result.add(new LotAllocation(a.lotId(), a.lotNumber(), a.expiresAt(), a.quantity().subtract(toSkip)));
                toSkip = BigDecimal.ZERO;
            } else {
                result.add(a);
            }
        }
        return result;
    }

    /** Satır indirimi lot parçalarına miktarla orantılı dağıtılır; yuvarlama farkı son parçaya yazılır. */
    static List<BigDecimal> splitDiscount(BigDecimal discount, BigDecimal quantity, List<LotAllocation> plan) {
        List<BigDecimal> result = new ArrayList<>();
        BigDecimal assigned = BigDecimal.ZERO;
        for (int i = 0; i < plan.size(); i++) {
            BigDecimal part = i == plan.size() - 1 ? discount.subtract(assigned)
                    : discount.multiply(plan.get(i).quantity()).divide(quantity, 2, RoundingMode.HALF_UP);
            assigned = assigned.add(part);
            result.add(part);
        }
        return result;
    }
}
