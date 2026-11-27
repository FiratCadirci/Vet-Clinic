package org.codeflow.veterineryproject.commission.policy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** FG-K4 Strategy seçimi (DB'siz): deneme aboneliğinde komisyon alınmaz, diğer her durumda platform kuralı. */
class CommissionPolicySelectorTest {

    private final PlatformRuleCommissionPolicy platformRule = new PlatformRuleCommissionPolicy();
    private final NoCommissionPolicy noCommission = new NoCommissionPolicy();
    private final CommissionPolicySelector selector = new SubscriptionCommissionPolicySelector(platformRule, noCommission);

    @Test
    void trialSubscriptionGetsNoCommissionPolicy() {
        CommissionPolicy policy = selector.select("TRIAL");

        assertThat(policy).isSameAs(noCommission);
        assertThat(policy.chargesCommission()).isFalse();
        assertThat(policy.code()).isEqualTo("NO_COMMISSION");
    }

    /** Ödemesi geciken, iptal eden ya da aboneliği olmayan organizasyon komisyondan muaf değildir. */
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"ACTIVE", "PAST_DUE", "CANCELLED", "EXPIRED"})
    void everyOtherStatusGetsPlatformRulePolicy(String status) {
        CommissionPolicy policy = selector.select(status);

        assertThat(policy).isSameAs(platformRule);
        assertThat(policy.chargesCommission()).isTrue();
        assertThat(policy.code()).isEqualTo("PLATFORM_RULE");
    }

    @Test
    void policyCodesMatchTheValuesTheTriggerUnderstands() {
        assertThat(platformRule.code()).isEqualTo(PlatformRuleCommissionPolicy.CODE);
        assertThat(noCommission.code()).isEqualTo(NoCommissionPolicy.CODE);
    }
}
