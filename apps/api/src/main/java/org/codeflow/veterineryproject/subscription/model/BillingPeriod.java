package org.codeflow.veterineryproject.subscription.model;

import java.time.OffsetDateTime;
import java.time.ZoneId;

/** Paket ödeme dönemi (vet.subscription_plans.billing_period). Ay/yıl eklemesi Türkiye saatine göre yapılır. */
public enum BillingPeriod {
    MONTHLY {
        @Override
        public OffsetDateTime addTo(OffsetDateTime start, ZoneId zone) {
            return start.atZoneSameInstant(zone).plusMonths(1).toOffsetDateTime();
        }
    },
    YEARLY {
        @Override
        public OffsetDateTime addTo(OffsetDateTime start, ZoneId zone) {
            return start.atZoneSameInstant(zone).plusYears(1).toOffsetDateTime();
        }
    };

    /** Dönem sonu; ay sonu taşması kırpılır (31 Ocak + 1 ay = 28/29 Şubat). */
    public abstract OffsetDateTime addTo(OffsetDateTime start, ZoneId zone);
}
