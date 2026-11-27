package org.codeflow.veterineryproject.commission.policy;

/** Organizasyona hangi komisyon stratejisinin uygulanacağına karar verir (Strategy deseninin bağlam tarafı). */
public interface CommissionPolicySelector {

    /**
     * @param subscriptionStatus organizasyonun güncel abonelik durumu (TRIAL, ACTIVE, PAST_DUE ...); aboneliği yoksa null
     */
    CommissionPolicy select(String subscriptionStatus);
}
