package org.codeflow.veterineryproject.inventory.service;

import org.codeflow.veterineryproject.common.event.DomainEvents.StockLowEvent;
import org.codeflow.veterineryproject.common.tenant.TenantContext;
import org.codeflow.veterineryproject.inventory.allocation.LotAllocationStrategy;
import org.codeflow.veterineryproject.inventory.dto.InventoryDto.LotAllocation;
import org.codeflow.veterineryproject.inventory.dto.InventoryDto.MovementResult;
import org.codeflow.veterineryproject.inventory.dto.InventoryDto.StockLevel;
import org.codeflow.veterineryproject.inventory.repository.InventoryJdbcRepository;
import org.codeflow.veterineryproject.inventory.repository.InventoryJdbcRepository.StockProduct;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Tüm stok hareketlerinin tek yazma yolu: DRAFT hareket + satırlar → POSTED. Negatif stok kuralı Java'da
 * tekrar yazılmaz; POSTED geçişindeki DB guard'ın 23514 hatası {@link InsufficientStockException}'a çevrilir.
 * Çağıranın transaction'ına katılır (hata olursa hareketin hiçbir parçası kalmaz).
 */
@Component
class StockLedger {

    static final String IN = "IN";
    static final String OUT = "OUT";
    static final ZoneId ZONE = ZoneId.of("Europe/Istanbul");

    private final InventoryJdbcRepository repository;
    private final LotAllocationStrategy allocation;
    private final ApplicationEventPublisher events;

    StockLedger(InventoryJdbcRepository repository, LotAllocationStrategy allocation, ApplicationEventPublisher events) {
        this.repository = repository;
        this.allocation = allocation;
        this.events = events;
    }

    /**
     * Lokasyondaki lotlardan stratejiye (FEFO) göre çıkış planı. Geçerli (süresi geçmemiş) stok yetmiyorsa 409.
     * {@code locationId} null ise (şubede hiç hareket yok) stok yoktur.
     */
    List<LotAllocation> allocate(UUID clinicId, UUID locationId, StockProduct product, BigDecimal quantity) {
        List<LotAllocation> lines = locationId == null ? List.of()
                : allocation.allocate(repository.buckets(locationId, product.id()), quantity, LocalDate.now(ZONE));
        BigDecimal allocated = lines.stream().map(LotAllocation::quantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (allocated.compareTo(quantity) < 0) {
            throw new InsufficientStockException(clinicId, product.id(), product.name());
        }
        return lines;
    }

    /** Tek yönlü (tüm satırlar IN ya da OUT) hareket yazar ve POSTED yapar. */
    MovementResult post(UUID transactionId, UUID clinicId, UUID locationId, StockProduct product, String type,
                        String direction, List<LotAllocation> lines, BigDecimal unitCost, String referenceType,
                        UUID referenceId, String notes) {
        TenantContext.ContextValues ctx = TenantContext.require();
        String number = "STK-" + transactionId.toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
        repository.insertTransaction(transactionId, ctx.organizationId(), locationId, number, type, referenceType,
                referenceId, notes, ctx.userId());
        for (LotAllocation l : lines) {
            repository.insertLine(ctx.organizationId(), transactionId, product.id(), l.lotId(), direction,
                    l.quantity(), unitCost);
        }
        try {
            repository.post(transactionId, ctx.userId());
        } catch (DataAccessException ex) {
            if (isStockGuard(ex)) {
                throw new InsufficientStockException(clinicId, product.id(), product.name());
            }
            throw ex;
        }

        BigDecimal after = repository.onHand(locationId, product.id());
        boolean low = StockLevel.isLow(after, product.minimumStock());
        if (OUT.equals(direction) && low) {
            BigDecimal moved = lines.stream().map(LotAllocation::quantity).reduce(BigDecimal.ZERO, BigDecimal::add);
            // Observer: yalnız minimumun üstünden altına inişte bir kez; bildirim commit sonrası dinleyicide.
            if (!StockLevel.isLow(after.add(moved), product.minimumStock())) {
                events.publishEvent(new StockLowEvent(ctx.organizationId(), clinicId, product.id(), product.name(),
                        after, product.minimumStock()));
            }
        }
        return new MovementResult(transactionId, number, type, lines,
                new StockLevel(clinicId, product.id(), after, product.minimumStock(), low));
    }

    /** vet.guard_inventory_stock_before_posting: "Yetersiz stok ..." USING ERRCODE 23514. */
    private static boolean isStockGuard(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql && "23514".equals(sql.getSQLState())
                    && String.valueOf(sql.getMessage()).contains("Yetersiz stok")) {
                return true;
            }
        }
        return false;
    }
}
