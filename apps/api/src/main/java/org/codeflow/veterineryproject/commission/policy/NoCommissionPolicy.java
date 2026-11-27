package org.codeflow.veterineryproject.commission.policy;

import org.springframework.stereotype.Component;

/** Komisyon alınmaz (deneme aboneliği). Tetikleyici bu kodlu faturaya komisyon kaydı yazmaz. */
@Component
public class NoCommissionPolicy implements CommissionPolicy {

    public static final String CODE = "NO_COMMISSION";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public boolean chargesCommission() {
        return false;
    }
}
