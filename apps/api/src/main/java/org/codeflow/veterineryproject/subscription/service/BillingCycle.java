package org.codeflow.veterineryproject.subscription.service;

import org.codeflow.veterineryproject.subscription.model.BillingPeriod;
import org.codeflow.veterineryproject.subscription.model.SubscriptionStatus;

import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * FG-K3 simüle ödemenin kapsadığı dönem (DB'siz, saf hesap). Dönemi süren (deneme/aktif) abonelikte yeni dönem mevcut
 * dönem sonundan başlar: erken ödeme kalan süreyi yakmaz, yenileme tarihi tam bir dönem ileri gider. Gecikmiş veya
 * iptal edilmiş abonelikte dönem ödeme anında başlar.
 */
public final class BillingCycle {

    private BillingCycle() {
    }

    public record PaidPeriod(OffsetDateTime start, OffsetDateTime end, boolean extendsCurrentPeriod) {
    }

    public static PaidPeriod next(SubscriptionStatus status, OffsetDateTime currentPeriodEnd, BillingPeriod period,
                                  OffsetDateTime now, ZoneId zone) {
        boolean extendsCurrent = status.isRunning() && currentPeriodEnd.isAfter(now);
        OffsetDateTime start = extendsCurrent ? currentPeriodEnd : now;
        return new PaidPeriod(start, period.addTo(start, zone), extendsCurrent);
    }
}
