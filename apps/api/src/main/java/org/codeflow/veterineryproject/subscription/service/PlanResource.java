package org.codeflow.veterineryproject.subscription.service;

/** Paket limitine tabi kaynaklar; DB fonksiyonu vet.plan_limit_status ile aynı kodları kullanır. */
public enum PlanResource {
    USERS("PLAN_USER_LIMIT", "Paket kullanıcı limitine ulaşıldı (en fazla %d aktif kullanıcı)."),
    BRANCHES("PLAN_BRANCH_LIMIT", "Paket şube limitine ulaşıldı (en fazla %d aktif şube).");

    private final String errorCode;
    private final String messageTemplate;

    PlanResource(String errorCode, String messageTemplate) {
        this.errorCode = errorCode;
        this.messageTemplate = messageTemplate;
    }

    public String errorCode() {
        return errorCode;
    }

    public String message(Integer limit) {
        return messageTemplate.formatted(limit == null ? 0 : limit);
    }
}
