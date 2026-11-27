package org.codeflow.veterineryproject.subscription.service;

import org.codeflow.veterineryproject.common.api.ConflictException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Paket limiti kontrolünün sonucu (DB'deki vet.plan_limit_status satırı). Kararı DB verir; bu sınıf yalnızca
 * sonucu API hatasına çevirir (409 + makine tarafından okunabilir kod).
 */
public record PlanLimitDecision(boolean allowed, Integer maxAllowed, Integer used, String planName, String reason) {

    public static final String SUBSCRIPTION_INACTIVE = "SUBSCRIPTION_INACTIVE";

    public void orThrow(PlanResource resource) {
        if (allowed) {
            return;
        }
        throw toException(resource, reason, maxAllowed, used);
    }

    public static ConflictException toException(PlanResource resource, String reason, Integer maxAllowed, Integer used) {
        if (SUBSCRIPTION_INACTIVE.equals(reason)) {
            return new ConflictException("Aktif bir abonelik olmadan yeni şube veya kullanıcı eklenemez.",
                    SUBSCRIPTION_INACTIVE, Map.of());
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("limit", maxAllowed);
        if (used != null) {
            details.put("used", used);
        }
        return new ConflictException(resource.message(maxAllowed), resource.errorCode(), details);
    }
}
