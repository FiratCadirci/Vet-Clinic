package org.codeflow.veterineryproject.billing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class BillingDto {

    private BillingDto() {
    }

    /**
     * Satış kalemi: hizmet (SERVICE + serviceId), ürün (PRODUCT + productId: ilaç, mama, aşı...) veya serbest kalem
     * (OTHER). Fiyatlar KDV hariç. İndirim tutar ({@code discountAmount}, TL) ya da oran ({@code discountRate}) olarak
     * verilir; ikisi birden verilirse tutar esastır. Lot takipli üründe lotId verilmezse kesimde FEFO ile seçilir.
     */
    public record LineRequest(
            @NotNull @Pattern(regexp = "SERVICE|PRODUCT|OTHER") String lineType,
            UUID serviceId,
            UUID productId,
            UUID lotId,
            @NotBlank @Size(max = 300) String description,
            @NotNull @DecimalMin(value = "0.001") BigDecimal quantity,
            @NotNull @DecimalMin("0.00") BigDecimal unitPrice,
            @DecimalMin("0") @DecimalMax("100") BigDecimal discountRate,
            @DecimalMin("0") @DecimalMax("100") BigDecimal taxRate,
            @DecimalMin("0.00") BigDecimal discountAmount) {
    }

    public record CreateInvoiceRequest(
            @NotNull UUID clientId,
            UUID animalId,
            UUID encounterId,
            UUID inventoryLocationId,
            LocalDate dueAt,
            @Size(max = 1000) String notes,
            @NotEmpty @Valid List<LineRequest> lines) {
    }

    /** Taslağın tüm kalemlerini topluca değiştirir (taslak düzenleyicisi). */
    public record ReplaceLinesRequest(@NotEmpty @Size(max = 200) @Valid List<LineRequest> lines) {
    }

    public record LineResponse(UUID id, int lineNo, String lineType, UUID serviceId, UUID productId, UUID lotId,
                               String description, BigDecimal quantity, BigDecimal unitPrice, BigDecimal discountRate,
                               BigDecimal taxRate, BigDecimal netAmount, BigDecimal taxAmount, BigDecimal lineTotal,
                               BigDecimal grossAmount, BigDecimal discountAmount, String lotNumber,
                               String productType) {
    }

    public record InvoiceSummary(UUID id, String invoiceNo, String status, UUID clinicId, UUID clientId,
                                 String clientName, UUID animalId, OffsetDateTime issuedAt, LocalDate dueAt,
                                 BigDecimal grandTotal, BigDecimal paidTotal, BigDecimal balance, String currency,
                                 OffsetDateTime createdAt, String animalName) {
    }

    /**
     * subtotal = KDV hariç net toplam (indirim düşülmüş), grossTotal = indirim öncesi brüt, discountTotal = indirim;
     * grossTotal − discountTotal + taxTotal = grandTotal. commissionPolicy kesimde uygulanan komisyon stratejisidir.
     */
    public record InvoiceResponse(InvoiceSummary invoice, BigDecimal subtotal, BigDecimal taxTotal, String notes,
                                  UUID encounterId, List<LineResponse> lines, List<PaymentResponse> payments,
                                  BigDecimal grossTotal, BigDecimal discountTotal, String commissionPolicy) {
    }

    public record PaymentRequest(
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            @NotNull @Pattern(regexp = "CASH|CREDIT_CARD|BANK_TRANSFER|ONLINE|OTHER") String paymentMethod,
            @Size(max = 150) String referenceNo,
            @Size(max = 1000) String notes) {
    }

    public record PaymentResponse(UUID paymentId, String paymentNo, String paymentMethod, BigDecimal appliedAmount,
                                  OffsetDateTime paidAt, String referenceNo) {
    }

    public record ClientBalance(UUID clientId, BigDecimal totalInvoiced, BigDecimal totalPaid, BigDecimal balance,
                                List<InvoiceSummary> openInvoices) {
    }

    public record MethodTotal(String paymentMethod, long count, BigDecimal total) {
    }

    public record DailyCashSummary(LocalDate date, UUID clinicId, List<MethodTotal> byMethod, BigDecimal totalCollected,
                                   long invoicesIssued, BigDecimal invoicedTotal, BigDecimal outstandingTotal) {
    }
}
