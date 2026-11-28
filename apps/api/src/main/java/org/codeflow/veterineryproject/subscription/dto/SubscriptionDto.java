package org.codeflow.veterineryproject.subscription.dto;

import jakarta.validation.constraints.NotNull;
import org.codeflow.veterineryproject.subscription.dto.PlanDto.PlanUsage;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Klinik abonelik ekranı DTO'ları (FG-K1..K3). Para alanları numeric (TL), limitler abonelik anındaki kopyalardır. */
public final class SubscriptionDto {

    private SubscriptionDto() {
    }

    /**
     * Kliniğin güncel (yoksa en son) aboneliği. {@code cancelAtPeriodEnd}: iptal istendi, dönem sonunda CANCELLED olur;
     * bu durumda {@code nextRenewalAt} boştur.
     */
    public record SubscriptionView(UUID id, UUID planId, String planCode, String planName, String status,
                                   String billingPeriod, BigDecimal price, String currency, OffsetDateTime startsAt,
                                   OffsetDateTime currentPeriodStart, OffsetDateTime currentPeriodEnd,
                                   OffsetDateTime nextRenewalAt, OffsetDateTime cancelledAt, boolean cancelAtPeriodEnd,
                                   int maxBranches, int maxUsers) {
    }

    public record CurrentSubscriptionResponse(SubscriptionView subscription, PlanUsage usage) {
    }

    /** Seçilen paket satırı dönemi de belirler (aylık ve yıllık satırlar ayrıdır). */
    public record ChangePlanRequest(@NotNull(message = "Paket seçilmelidir.") UUID planId) {
    }

    /** subscription_billing_events satırı (simüle ödeme). */
    public record BillingEventResponse(UUID id, String eventNo, BigDecimal amount, String currency, String status,
                                       String paymentMode, OffsetDateTime periodStart, OffsetDateTime periodEnd,
                                       OffsetDateTime processedAt) {
    }

    public record PaymentResult(BillingEventResponse payment, CurrentSubscriptionResponse current) {
    }

    /** Günlük yenileme kontrolü sonucu: işlenen organizasyon ve gecikmiş / iptal olan abonelik sayısı. */
    public record RenewalRunResponse(int organizations, int pastDue, int cancelled) {
    }
}
