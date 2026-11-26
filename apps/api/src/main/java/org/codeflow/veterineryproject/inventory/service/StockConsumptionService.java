package org.codeflow.veterineryproject.inventory.service;

import jakarta.persistence.EntityNotFoundException;
import org.codeflow.veterineryproject.common.audit.AuditRecorder;
import org.codeflow.veterineryproject.common.tenant.TenantContext;
import org.codeflow.veterineryproject.inventory.dto.InventoryDto.ConsumptionResult;
import org.codeflow.veterineryproject.inventory.dto.InventoryDto.LotAllocation;
import org.codeflow.veterineryproject.inventory.dto.InventoryDto.MovementResult;
import org.codeflow.veterineryproject.inventory.repository.InventoryJdbcRepository;
import org.codeflow.veterineryproject.inventory.repository.InventoryJdbcRepository.StockProduct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Diğer modüllerin (aşı kaydı, satış/fatura, tedavi) kullandığı stok tüketim API'si (FG-F4).
 *
 * <pre>
 * // Çağıranın @Transactional metodu içinde (aynı transaction'a katılır; hata olursa çağıranın işi de geri alınır):
 * ConsumptionResult r = stockConsumption.consume(ctx.clinicId(), productId, new BigDecimal("1"), "VACCINE_RECORD", recordId);
 * r.allocations();   // hangi lottan ne kadar düşüldü (aşı kaydına lot no yazmak için)
 *
 * // Yalnız dağıtım planı (hareket yazmaz), ör. fatura satırlarını lotlara bölmek için:
 * List&lt;LotAllocation&gt; plan = stockConsumption.allocateFefo(ctx.clinicId(), productId, quantity);
 * </pre>
 *
 * Yetersiz stokta {@link InsufficientStockException} (409, INSUFFICIENT_STOCK) fırlatılır; eşzamanlı tüketimde
 * son karar DB stok guard'ınındır (23514 → aynı istisna). Bilinmeyen/başka organizasyonun ürünü 404.
 */
@Service
public class StockConsumptionService {

    private final InventoryJdbcRepository repository;
    private final StockLedger ledger;
    private final AuditRecorder audit;

    public StockConsumptionService(InventoryJdbcRepository repository, StockLedger ledger, AuditRecorder audit) {
        this.repository = repository;
        this.ledger = ledger;
        this.audit = audit;
    }

    /**
     * Şubenin varsayılan stok lokasyonunda FEFO dağıtımı yapar: SKT'si en yakın, süresi geçmemiş lotlardan başlar;
     * lot takipsiz üründe lotId = null tek satır döner. Hareket yazmaz.
     *
     * @return toplamı {@code quantity} olan dağıtım; stok kalemi olmayan üründe boş liste
     * @throws InsufficientStockException geçerli stok yetmiyorsa (409)
     */
    @Transactional(readOnly = true)
    public List<LotAllocation> allocateFefo(UUID clinicId, UUID productId, BigDecimal quantity) {
        requirePositive(quantity);
        StockProduct product = product(productId);
        if (!product.stockItem()) {
            return List.of();
        }
        return ledger.allocate(clinicId, repository.defaultLocation(clinicId).orElse(null), product, quantity);
    }

    /**
     * FEFO ile stoktan düşer: DISPENSE hareketi (reference_type / reference_id ile) yazar ve POSTED yapar.
     * Stok minimumun altına inerse StockLowEvent yayınlanır (commit sonrası dinlenir).
     *
     * @param referenceType kaynağın türü, ör. "VACCINE_RECORD", "INVOICE", "ENCOUNTER_TREATMENT" (≤ 50 karakter)
     * @param referenceId   kaynağın kimliği (null olabilir)
     * @return hareket ve lot dağılımı; stok kalemi olmayan üründe hareket yazılmaz (transactionId null)
     * @throws InsufficientStockException yetersiz stokta (409); hiçbir şey yazılmaz
     */
    @Transactional
    public ConsumptionResult consume(UUID clinicId, UUID productId, BigDecimal quantity, String referenceType,
                                     UUID referenceId) {
        requirePositive(quantity);
        StockProduct product = product(productId);
        if (!product.stockItem()) {
            return new ConsumptionResult(null, null, List.of(), null);
        }
        UUID location = repository.ensureDefaultLocation(TenantContext.require().organizationId(), clinicId);
        List<LotAllocation> lines = ledger.allocate(clinicId, location, product, quantity);
        MovementResult r = ledger.post(UUID.randomUUID(), clinicId, location, product, "DISPENSE", StockLedger.OUT,
                lines, BigDecimal.ZERO, referenceType, referenceId, null);
        audit.record("STOCK_DISPENSE", "INVENTORY_TRANSACTION", r.transactionId(), clinicId);
        return new ConsumptionResult(r.transactionId(), r.transactionNo(), lines, r.stock());
    }

    /** Şubenin varsayılan stok lokasyonu (yoksa oluşturulur); fatura inventory_location_id alanı için. */
    @Transactional
    public UUID defaultLocationId(UUID clinicId) {
        return repository.ensureDefaultLocation(TenantContext.require().organizationId(), clinicId);
    }

    private StockProduct product(UUID productId) {
        return repository.product(productId).orElseThrow(() -> new EntityNotFoundException("Ürün bulunamadı."));
    }

    private static void requirePositive(BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException("Miktar sıfırdan büyük olmalıdır.");
        }
    }
}
