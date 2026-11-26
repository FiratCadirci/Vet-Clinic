package org.codeflow.veterineryproject.inventory.service;

import jakarta.persistence.EntityNotFoundException;
import org.codeflow.veterineryproject.common.api.BusinessRuleException;
import org.codeflow.veterineryproject.common.audit.AuditRecorder;
import org.codeflow.veterineryproject.common.tenant.TenantContext;
import org.codeflow.veterineryproject.inventory.dto.InventoryDto.AdjustmentRequest;
import org.codeflow.veterineryproject.inventory.dto.InventoryDto.LotAllocation;
import org.codeflow.veterineryproject.inventory.dto.InventoryDto.MovementResult;
import org.codeflow.veterineryproject.inventory.dto.InventoryDto.ReceiptRequest;
import org.codeflow.veterineryproject.inventory.dto.InventoryDto.TransferRequest;
import org.codeflow.veterineryproject.inventory.dto.InventoryDto.TransferResult;
import org.codeflow.veterineryproject.inventory.repository.InventoryJdbcRepository;
import org.codeflow.veterineryproject.inventory.repository.InventoryJdbcRepository.LotRow;
import org.codeflow.veterineryproject.inventory.repository.InventoryJdbcRepository.StockProduct;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Şube stoğuna giriş, sayım düzeltmesi ve şubeler arası transfer (FG-F2, FG-F3). Şube her zaman X-Clinic-Id'dir.
 * Her işlem tek transaction'dır; stok yetmezse DB guard reddeder ve hiçbir hareket kalmaz.
 */
@Service
public class StockMovementService {

    private final InventoryJdbcRepository repository;
    private final StockLedger ledger;
    private final AuditRecorder audit;

    public StockMovementService(InventoryJdbcRepository repository, StockLedger ledger, AuditRecorder audit) {
        this.repository = repository;
        this.ledger = ledger;
        this.audit = audit;
    }

    @Transactional
    public MovementResult receive(ReceiptRequest r) {
        TenantContext.ContextValues ctx = requireClinic();
        StockProduct product = activeStockProduct(r.productId());
        BigDecimal unitCost = r.unitCost() == null ? product.purchasePrice() : r.unitCost();
        LotAllocation line = new LotAllocation(null, null, null, r.quantity());
        if (product.tracksLot()) {
            String lotNumber = blankToNull(r.lotNumber());
            if (lotNumber == null || r.expiresAt() == null) {
                throw new BusinessRuleException("Parti takipli üründe parti numarası ve son kullanma tarihi zorunludur.");
            }
            LotRow lot = receivableLot(ctx, product, lotNumber, r.expiresAt(), unitCost);
            line = new LotAllocation(lot.id(), lot.lotNumber(), lot.expiresAt(), r.quantity());
        }
        UUID location = repository.ensureDefaultLocation(ctx.organizationId(), ctx.clinicId());
        MovementResult result = ledger.post(UUID.randomUUID(), ctx.clinicId(), location, product, "RECEIPT",
                StockLedger.IN, List.of(line), unitCost, null, null, blankToNull(r.notes()));
        audit.record("STOCK_RECEIPT", "INVENTORY_TRANSACTION", result.transactionId(), ctx.clinicId());
        return result;
    }

    /** Sayım farkı: pozitif fazla (IN), negatif eksik (OUT). Eksik stoktan fazlaysa DB guard reddeder (409). */
    @Transactional
    public MovementResult adjust(AdjustmentRequest r) {
        TenantContext.ContextValues ctx = requireClinic();
        if (r.quantityDelta().signum() == 0) {
            throw new IllegalArgumentException("Sayım farkı sıfır olamaz.");
        }
        StockProduct product = activeStockProduct(r.productId());
        boolean surplus = r.quantityDelta().signum() > 0;
        BigDecimal quantity = r.quantityDelta().abs();
        LotAllocation line = new LotAllocation(null, null, null, quantity);
        if (product.tracksLot()) {
            String lotNumber = blankToNull(r.lotNumber());
            if (lotNumber == null) {
                throw new BusinessRuleException("Parti takipli üründe parti numarası zorunludur.");
            }
            LotRow lot = repository.findLot(product.id(), lotNumber).orElse(null);
            if (lot == null) {
                if (!surplus) {
                    throw new BusinessRuleException("Bu parti numarası ürün için kayıtlı değil.");
                }
                if (r.expiresAt() == null) {
                    throw new BusinessRuleException("Yeni parti için son kullanma tarihi zorunludur.");
                }
                lot = receivableLot(ctx, product, lotNumber, r.expiresAt(), product.purchasePrice());
            }
            line = new LotAllocation(lot.id(), lot.lotNumber(), lot.expiresAt(), quantity);
        }
        UUID location = repository.ensureDefaultLocation(ctx.organizationId(), ctx.clinicId());
        MovementResult result = ledger.post(UUID.randomUUID(), ctx.clinicId(), location, product, "ADJUSTMENT",
                surplus ? StockLedger.IN : StockLedger.OUT, List.of(line), product.purchasePrice(), null, null,
                r.reason().trim());
        audit.record("STOCK_ADJUSTMENT", "INVENTORY_TRANSACTION", result.transactionId(), ctx.clinicId());
        return result;
    }

    /**
     * Kaynakta TRANSFER_OUT, hedefte TRANSFER_IN; tek transaction. Lot verilmezse FEFO ile dağıtılır ve lotlar
     * SKT'leriyle hedefe taşınır. Hedef şube kullanıcının şubelerinden biri ve aynı klinik grubunda olmalıdır.
     */
    @Transactional
    public TransferResult transfer(TransferRequest r, Collection<UUID> userClinicIds) {
        TenantContext.ContextValues ctx = requireClinic();
        UUID target = r.targetClinicId();
        if (target.equals(ctx.clinicId())) {
            throw new BusinessRuleException("Kaynak ve hedef şube aynı olamaz.");
        }
        if (userClinicIds == null || !userClinicIds.contains(target)) {
            throw new AccessDeniedException("Hedef şube için yetkiniz yok.");
        }
        if (!repository.sameClinicGroup(ctx.organizationId(), ctx.clinicId(), target)) {
            throw new BusinessRuleException("Transfer yalnızca aynı klinik grubundaki şubeler arasında yapılabilir.");
        }
        StockProduct product = activeStockProduct(r.productId());
        UUID source = repository.ensureDefaultLocation(ctx.organizationId(), ctx.clinicId());
        UUID destination = repository.ensureDefaultLocation(ctx.organizationId(), target);

        List<LotAllocation> lines;
        String lotNumber = blankToNull(r.lotNumber());
        if (lotNumber != null) {
            LotRow lot = repository.findLot(product.id(), lotNumber)
                    .orElseThrow(() -> new BusinessRuleException("Bu parti numarası ürün için kayıtlı değil."));
            lines = List.of(new LotAllocation(lot.id(), lot.lotNumber(), lot.expiresAt(), r.quantity()));
        } else {
            lines = ledger.allocate(ctx.clinicId(), source, product, r.quantity());
        }

        UUID outId = UUID.randomUUID();
        UUID inId = UUID.randomUUID();
        String notes = blankToNull(r.notes());
        MovementResult out = ledger.post(outId, ctx.clinicId(), source, product, "TRANSFER_OUT", StockLedger.OUT,
                lines, product.purchasePrice(), "TRANSFER", inId, notes);
        MovementResult in = ledger.post(inId, target, destination, product, "TRANSFER_IN", StockLedger.IN,
                lines, product.purchasePrice(), "TRANSFER", outId, notes);
        audit.record("STOCK_TRANSFER", "INVENTORY_TRANSACTION", outId, ctx.clinicId());
        return new TransferResult(out, in);
    }

    /** Lot organizasyon genelidir; aynı lot no farklı SKT ile girilemez. */
    private LotRow receivableLot(TenantContext.ContextValues ctx, StockProduct product, String lotNumber,
                                 LocalDate expiresAt, BigDecimal unitCost) {
        if (expiresAt.isBefore(LocalDate.now(StockLedger.ZONE))) {
            throw new BusinessRuleException("Son kullanma tarihi geçmiş parti stoğa girilemez.");
        }
        LotRow lot = repository.ensureLot(ctx.organizationId(), product.id(), lotNumber, expiresAt, unitCost);
        if (!Objects.equals(lot.expiresAt(), expiresAt)) {
            throw new BusinessRuleException("Bu parti numarası farklı bir son kullanma tarihiyle kayıtlı (%s)."
                    .formatted(lot.expiresAt()));
        }
        return lot;
    }

    private StockProduct activeStockProduct(UUID productId) {
        StockProduct p = repository.product(productId)
                .orElseThrow(() -> new EntityNotFoundException("Ürün bulunamadı."));
        if (!p.active()) {
            throw new BusinessRuleException("Pasif ürün için stok işlemi yapılamaz.");
        }
        if (!p.stockItem()) {
            throw new BusinessRuleException("Bu ürün stok kalemi değil.");
        }
        return p;
    }

    private static TenantContext.ContextValues requireClinic() {
        TenantContext.ContextValues ctx = TenantContext.require();
        if (ctx.clinicId() == null) {
            throw new BusinessRuleException("Bu işlem için şube (X-Clinic-Id) gereklidir.");
        }
        return ctx;
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
