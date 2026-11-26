package org.codeflow.veterineryproject.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Stok uç noktalarının istek/yanıt kayıtları. Miktarlar ürün birimindedir (numeric(14,3)). */
public final class InventoryDto {

    private InventoryDto() {
    }

    // ---------------------------------------------------------------- istekler

    /** Stok girişi (alım). Parti takipli üründe lotNumber + expiresAt zorunludur (FG-F2). */
    public record ReceiptRequest(
            @NotNull UUID productId,
            @NotNull @DecimalMin("0.001") BigDecimal quantity,
            @Size(max = 120) String lotNumber,
            LocalDate expiresAt,
            @DecimalMin("0") BigDecimal unitCost,
            @Size(max = 500) String notes) {
    }

    /** Sayım düzeltmesi: pozitif fark fazla (IN), negatif fark eksik (OUT). Gerekçe zorunlu. */
    public record AdjustmentRequest(
            @NotNull UUID productId,
            @NotNull BigDecimal quantityDelta,
            @Size(max = 120) String lotNumber,
            LocalDate expiresAt,
            @NotBlank @Size(min = 3, max = 500) String reason) {
    }

    /** Şubeler arası transfer: kaynak X-Clinic-Id, hedef aynı klinik grubunda ve kullanıcının erişebildiği şube. */
    public record TransferRequest(
            @NotNull UUID targetClinicId,
            @NotNull UUID productId,
            @NotNull @DecimalMin("0.001") BigDecimal quantity,
            @Size(max = 120) String lotNumber,
            @Size(max = 500) String notes) {
    }

    // ---------------------------------------------------------------- yanıtlar

    /** Bir ürünün şubedeki (varsayılan lokasyon) bakiyesi. */
    public record StockLevel(UUID clinicId, UUID productId, BigDecimal onHand, BigDecimal minimumStock, boolean low) {

        /** Minimum tanımlıysa (>0) bakiye minimuma indiğinde/altına düştüğünde düşük stok sayılır. */
        public static boolean isLow(BigDecimal onHand, BigDecimal minimum) {
            return minimum.signum() > 0 && onHand.compareTo(minimum) <= 0;
        }
    }

    /** Bir lottan (veya lotsuz stoktan: lotId = null) ayrılan/düşülen miktar. */
    public record LotAllocation(UUID lotId, String lotNumber, LocalDate expiresAt, BigDecimal quantity) {
    }

    public record MovementResult(UUID transactionId, String transactionNo, String transactionType,
                                 List<LotAllocation> lines, StockLevel stock) {
    }

    public record TransferResult(MovementResult source, MovementResult target) {
    }

    /** FEFO tüketim sonucu. Stok kalemi olmayan üründe hareket yazılmaz: transactionId null, allocations boş. */
    public record ConsumptionResult(UUID transactionId, String transactionNo, List<LotAllocation> allocations,
                                    StockLevel stock) {
    }

    public record StockLot(UUID lotId, String lotNumber, LocalDate expiresAt, BigDecimal quantity, boolean expired,
                           boolean expiringSoon) {
    }

    /** Şube stok durumu satırı: ürün toplamı + lot dağılımı (SKT sırasıyla). */
    public record StockItem(UUID clinicId, UUID productId, String name, String productType, String barcode,
                            String baseUnit, BigDecimal minimumStock, boolean tracksLot, BigDecimal onHand,
                            boolean low, boolean expiringSoon, List<StockLot> lots) {
    }

    public record LowStockAlert(UUID clinicId, UUID productId, String name, String baseUnit, BigDecimal onHand,
                                BigDecimal minimumStock) {
    }

    public record ExpiryAlert(UUID clinicId, UUID productId, String name, UUID lotId, String lotNumber,
                              LocalDate expiresAt, BigDecimal quantity, boolean expired, long daysLeft) {
    }

    public record StockAlerts(int expiryDays, List<LowStockAlert> lowStock, List<ExpiryAlert> expiring) {
    }

    /** Hareket geçmişi satırı (POSTED hareketin bir satırı). quantity işaretlidir: giriş +, çıkış −. */
    public record MovementResponse(UUID id, UUID transactionId, String transactionNo, String transactionType,
                                   UUID clinicId, UUID productId, String productName, UUID lotId, String lotNumber,
                                   BigDecimal quantity, String referenceType, UUID referenceId, String notes,
                                   UUID createdBy, String createdByName, OffsetDateTime occurredAt) {
    }
}
