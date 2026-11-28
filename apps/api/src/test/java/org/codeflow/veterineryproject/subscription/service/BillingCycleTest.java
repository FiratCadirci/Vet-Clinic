package org.codeflow.veterineryproject.subscription.service;

import org.codeflow.veterineryproject.common.time.ApiDateTime;
import org.codeflow.veterineryproject.subscription.model.BillingPeriod;
import org.codeflow.veterineryproject.subscription.model.SubscriptionStatus;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** FG-K3 simüle ödeme dönemi hesabı (DB'siz). */
class BillingCycleTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-15T10:00:00+03:00");

    @Test
    void runningSubscription_paidEarly_extendsFromCurrentPeriodEnd() {
        OffsetDateTime trialEnd = OffsetDateTime.parse("2026-11-29T10:00:00+03:00");

        BillingCycle.PaidPeriod period = BillingCycle.next(SubscriptionStatus.TRIAL, trialEnd, BillingPeriod.MONTHLY, NOW,
                ApiDateTime.ZONE);

        assertThat(period.extendsCurrentPeriod()).isTrue();
        assertThat(period.start()).isEqualTo(trialEnd);
        assertThat(period.end()).isAtSameInstantAs(OffsetDateTime.parse("2026-12-29T10:00:00+03:00"));
    }

    @Test
    void pastDueOrCancelled_newPeriodStartsAtPaymentTime() {
        OffsetDateTime ended = NOW.minusDays(3);

        for (SubscriptionStatus status : new SubscriptionStatus[]{SubscriptionStatus.PAST_DUE, SubscriptionStatus.CANCELLED}) {
            BillingCycle.PaidPeriod period = BillingCycle.next(status, ended, BillingPeriod.YEARLY, NOW, ApiDateTime.ZONE);
            assertThat(period.extendsCurrentPeriod()).isFalse();
            assertThat(period.start()).isEqualTo(NOW);
            assertThat(period.end()).isAtSameInstantAs(OffsetDateTime.parse("2027-11-15T10:00:00+03:00"));
        }
    }

    @Test
    void activeButAlreadyEnded_isTreatedAsLapsed() {
        BillingCycle.PaidPeriod period = BillingCycle.next(SubscriptionStatus.ACTIVE, NOW.minusMinutes(1),
                BillingPeriod.MONTHLY, NOW, ApiDateTime.ZONE);

        assertThat(period.extendsCurrentPeriod()).isFalse();
        assertThat(period.start()).isEqualTo(NOW);
    }

    @Test
    void monthEndIsClampedInIstanbulTime() {
        OffsetDateTime jan31 = OffsetDateTime.parse("2027-01-31T00:30:00+03:00");

        assertThat(BillingPeriod.MONTHLY.addTo(jan31, ApiDateTime.ZONE))
                .isAtSameInstantAs(OffsetDateTime.parse("2027-02-28T00:30:00+03:00"));
        // UTC'de hâlâ 30 Ocak olan an, İstanbul gününe göre ilerler.
        assertThat(BillingPeriod.MONTHLY.addTo(OffsetDateTime.parse("2027-01-30T21:30:00Z"), ApiDateTime.ZONE))
                .isAtSameInstantAs(OffsetDateTime.parse("2027-02-28T00:30:00+03:00"));
    }
}
