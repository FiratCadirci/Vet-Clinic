package org.codeflow.veterineryproject.commission.policy;

import org.springframework.stereotype.Component;

/**
 * Abonelik durumuna göre seçim: TRIAL → {@link NoCommissionPolicy} (deneme sürümünde komisyon alınmaz).
 * ACTIVE, PAST_DUE, iptal edilmiş ya da aboneliği olmayan organizasyon → {@link PlatformRuleCommissionPolicy};
 * aboneliği bırakmak komisyondan kaçmanın yolu olmamalıdır. Karar kesim anındaki duruma göre verilir.
 */
@Component
public class SubscriptionCommissionPolicySelector implements CommissionPolicySelector {

    private final PlatformRuleCommissionPolicy platformRule;
    private final NoCommissionPolicy noCommission;

    public SubscriptionCommissionPolicySelector(PlatformRuleCommissionPolicy platformRule, NoCommissionPolicy noCommission) {
        this.platformRule = platformRule;
        this.noCommission = noCommission;
    }

    @Override
    public CommissionPolicy select(String subscriptionStatus) {
        return "TRIAL".equals(subscriptionStatus) ? noCommission : platformRule;
    }
}
