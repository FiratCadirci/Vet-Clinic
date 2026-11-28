package org.codeflow.veterineryproject.subscription.service;

import org.codeflow.veterineryproject.common.api.ConflictException;
import org.codeflow.veterineryproject.subscription.dto.PlanDto.PlanUsage;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * FG-K2 (Specification): bir paketin limitleri mevcut kullanımı karşılıyor mu? Kullanım (aktif şube / personel)
 * yeni paketin limitini aşıyorsa paket düşürülemez; kullanıcı önce şube/personel pasifleştirmelidir.
 */
public record PlanCapacity(String planName, int maxBranches, int maxUsers) {

    public static final String DOWNGRADE_BLOCKED = "PLAN_DOWNGRADE_BLOCKED";

    public boolean accepts(PlanUsage usage) {
        return usage.activeBranches() <= maxBranches && usage.activeUsers() <= maxUsers;
    }

    public void assertAccepts(PlanUsage usage) {
        if (accepts(usage)) {
            return;
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("activeBranches", usage.activeBranches());
        details.put("maxBranches", maxBranches);
        details.put("activeUsers", usage.activeUsers());
        details.put("maxUsers", maxUsers);
        throw new ConflictException(("%s paketine geçilemez: mevcut kullanım (%d şube, %d kullanıcı) paket limitlerini "
                + "(%d şube, %d kullanıcı) aşıyor.").formatted(planName, usage.activeBranches(), usage.activeUsers(),
                maxBranches, maxUsers), DOWNGRADE_BLOCKED, details);
    }
}
