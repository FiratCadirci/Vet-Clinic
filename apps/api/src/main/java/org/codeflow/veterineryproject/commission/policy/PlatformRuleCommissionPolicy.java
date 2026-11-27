package org.codeflow.veterineryproject.commission.policy;

import org.springframework.stereotype.Component;

/**
 * Standart yöntem: mama ve ilaç kalemlerinin KDV hariç net tutarına, kesim anında aktif platform kuralının
 * oranı uygulanır. Kurallar platform yöneticisine aittir; klinik ürün kartından komisyonu kapatamaz.
 */
@Component
public class PlatformRuleCommissionPolicy implements CommissionPolicy {

    public static final String CODE = "PLATFORM_RULE";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public boolean chargesCommission() {
        return true;
    }
}
