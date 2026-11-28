package org.codeflow.veterineryproject.platform.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Platform yöneticisi paneli DTO'ları (FG-K5). Klinik listesi yalnız ticari bilgileri taşır (ad, kod, paket, durum,
 * kullanım, gelir); hasta, sahip, muayene gibi tıbbi/kişisel veri içermez.
 */
public final class PlatformDto {

    public static final String SUBSCRIPTION_STATUSES = "TRIAL|ACTIVE|PAST_DUE|CANCELLED|EXPIRED";
    public static final String BILLING_PERIODS = "MONTHLY|YEARLY";
    /** FG-K4: komisyon yalnız mama ve ilaç kalemlerinden alınır; kural da yalnız bu ürün tipleri için tanımlanır. */
    public static final String COMMISSION_PRODUCT_TYPES = "FOOD|MEDICATION";

    private PlatformDto() {
    }

    // ------------------------------------------------------------------ klinikler

    public record TenantSubscription(UUID id, String status, UUID planId, String planCode, String planName,
                                     String billingPeriod, BigDecimal price, String currency,
                                     OffsetDateTime currentPeriodEnd, OffsetDateTime nextRenewalAt,
                                     OffsetDateTime cancelledAt, int maxBranches, int maxUsers) {
    }

    public record TenantResponse(UUID id, Integer code, String name, String email, String phone, boolean active,
                                 OffsetDateTime createdAt, TenantSubscription subscription, int activeBranches,
                                 int activeUsers, BigDecimal subscriptionRevenue, BigDecimal commissionRevenue) {
    }

    /**
     * Platform müdahalesi: paket, durum (ör. PAST_DUE / CANCELLED ile askıya alma) ve dönem sonu. Paket limit kontrolü
     * yapılmaz (FG-K2 klinik yöneticisinin paket düşürmesi içindir).
     */
    public record UpdateTenantSubscriptionRequest(
            UUID planId,
            @Pattern(regexp = SUBSCRIPTION_STATUSES, message = "Durum TRIAL, ACTIVE, PAST_DUE, CANCELLED veya EXPIRED olmalıdır.")
            String status,
            OffsetDateTime currentPeriodEnd
    ) {
        @AssertTrue(message = "Değiştirilecek en az bir alan (paket, durum veya dönem sonu) gönderilmelidir.")
        public boolean isAnyChange() {
            return planId != null || status != null || currentPeriodEnd != null;
        }
    }

    // ------------------------------------------------------------------ paketler

    public record PlanResponse(UUID id, String code, String name, String billingPeriod, BigDecimal price, String currency,
                               int maxBranches, int maxUsers, boolean active, int subscriptionCount,
                               OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    }

    public record CreatePlanRequest(
            @NotBlank @Pattern(regexp = "^[a-z0-9-]{2,50}$", message = "Kod 2-50 karakter olmalı; küçük harf, rakam ve tire içerebilir.")
            String code,
            @NotBlank @Size(min = 2, max = 120) String name,
            @NotNull @Pattern(regexp = BILLING_PERIODS, message = "Dönem MONTHLY veya YEARLY olmalıdır.") String billingPeriod,
            @NotNull @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal price,
            @NotNull @Min(1) Integer maxBranches,
            @NotNull @Min(1) Integer maxUsers,
            Boolean active
    ) {
    }

    /** Kod ve dönem sonradan değişmez (abonelikler ve kayıt ekranı bunlara bağlıdır); fiyat/limit değişikliği mevcut aboneliklere yansımaz. */
    public record UpdatePlanRequest(
            @NotBlank @Size(min = 2, max = 120) String name,
            @NotNull @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal price,
            @NotNull @Min(1) Integer maxBranches,
            @NotNull @Min(1) Integer maxUsers,
            @NotNull Boolean active
    ) {
    }

    // ------------------------------------------------------------------ komisyon kuralları

    /** {@code inEffect}: aktif ve geçerlilik aralığı şu anı kapsıyor (fatura trigger'ı bu kuralları uygular). */
    public record CommissionRuleResponse(UUID id, String code, String name, String productType, BigDecimal ratePercent,
                                         OffsetDateTime validFrom, OffsetDateTime validTo, boolean active,
                                         boolean inEffect, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    }

    public record CreateCommissionRuleRequest(
            @NotNull @Pattern(regexp = COMMISSION_PRODUCT_TYPES, message = "Komisyon kuralı yalnız mama (FOOD) veya ilaç (MEDICATION) için tanımlanabilir.")
            String productType,
            @NotNull @DecimalMin("0.00") @DecimalMax("100.00") @Digits(integer = 3, fraction = 2) BigDecimal ratePercent,
            OffsetDateTime validFrom,
            OffsetDateTime validTo,
            @Size(max = 150) String name,
            Boolean active
    ) {
        @AssertTrue(message = "Geçerlilik bitişi başlangıçtan sonra olmalıdır.")
        public boolean isValidityOrdered() {
            return validFrom == null || validTo == null || validTo.isAfter(validFrom);
        }
    }

    /** Ürün tipi sonradan değişmez; farklı tip için yeni kural açılır. Oran değişikliği yalnız yeni faturaları etkiler. */
    public record UpdateCommissionRuleRequest(
            @NotNull @DecimalMin("0.00") @DecimalMax("100.00") @Digits(integer = 3, fraction = 2) BigDecimal ratePercent,
            @NotNull OffsetDateTime validFrom,
            OffsetDateTime validTo,
            @Size(max = 150) String name,
            @NotNull Boolean active
    ) {
        @AssertTrue(message = "Geçerlilik bitişi başlangıçtan sonra olmalıdır.")
        public boolean isValidityOrdered() {
            return validFrom == null || validTo == null || validTo.isAfter(validFrom);
        }
    }

    // ------------------------------------------------------------------ raporlar

    public record MonthlySubscriptionRevenue(String month, int paymentCount, BigDecimal amount) {
    }

    public record MonthlyCommission(String month, BigDecimal eligibleAmount, BigDecimal commissionAmount) {
    }

    public record RevenueTotals(BigDecimal subscription, BigDecimal commission, BigDecimal total) {
    }

    /** Gelir raporu: abonelik ödemeleri (SUCCEEDED) ve komisyonlar (VOID hariç), Türkiye saatine göre aylık. */
    public record RevenueReport(LocalDate from, LocalDate to, RevenueTotals totals,
                                List<MonthlySubscriptionRevenue> subscriptionsByMonth,
                                List<MonthlyCommission> commissionsByMonth) {
    }

    public record OrganizationCommission(UUID organizationId, Integer organizationCode, String organizationName,
                                         int recordCount, BigDecimal eligibleAmount, BigDecimal commissionAmount) {
    }

    /** Komisyon raporu: klinik (organizasyon) bazında toplamlar. */
    public record CommissionReport(LocalDate from, LocalDate to, BigDecimal totalEligible, BigDecimal totalCommission,
                                   List<OrganizationCommission> organizations) {
    }
}
