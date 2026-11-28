package org.codeflow.veterineryproject.subscription.model;

/** vet.organization_subscriptions.status CHECK değerleri. TRIAL/ACTIVE/PAST_DUE "güncel" abonelik sayılır (tekil indeks). */
public enum SubscriptionStatus {
    TRIAL, ACTIVE, PAST_DUE, CANCELLED, EXPIRED;

    /** Dönemi hâlâ geçerli sayılabilen (ödenmiş ya da deneme) durumlar: erken ödeme dönemi uzatır. */
    public boolean isRunning() {
        return this == TRIAL || this == ACTIVE;
    }

    public boolean isTerminal() {
        return this == CANCELLED || this == EXPIRED;
    }
}
