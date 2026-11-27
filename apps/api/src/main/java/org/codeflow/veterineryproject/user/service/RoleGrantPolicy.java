package org.codeflow.veterineryproject.user.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * FG-A5 / FG-A4 yetki verme kuralları (DB'siz, saf iş kuralı):
 * <ul>
 *   <li>Klinik yöneticisi rolünü yalnızca klinik yöneticisi verebilir; klinik yöneticisinin hesabını yalnızca klinik
 *       yöneticisi değiştirebilir.</li>
 *   <li>Şube yöneticisi yalnızca kendi şubelerine atama yapar ve yalnızca kendi şubelerinde çalışan personeli yönetir.</li>
 * </ul>
 * Davet, rol ataması, pasifleştirme ve aktifleştirme aynı politikayı kullanır.
 */
@Component
public class RoleGrantPolicy {

    public static final String CLINIC_ADMIN = "clinic_admin";
    public static final String BRANCH_MANAGER = "branch_manager";

    /** Çağıranın rol özeti: klinik yöneticisi mi, şube yöneticisi mi, aktif şubeleri hangileri. */
    public record Caller(boolean clinicAdmin, boolean branchManager, Set<UUID> clinicIds) {
        /** Klinik yöneticisi olmayan şube yöneticisi yalnızca kendi şubelerinde işlem yapar. */
        public boolean branchScoped() {
            return !clinicAdmin && branchManager;
        }
    }

    public void checkGrant(Caller caller, String roleCode, Collection<UUID> clinicIds) {
        if (CLINIC_ADMIN.equals(roleCode) && !caller.clinicAdmin()) {
            throw new AccessDeniedException("Klinik yöneticisi yetkisini yalnızca klinik yöneticisi verebilir.");
        }
        if (caller.branchScoped() && !caller.clinicIds().containsAll(clinicIds)) {
            throw new AccessDeniedException("Şube yöneticisi yalnızca kendi şubelerine atama yapabilir.");
        }
    }

    public void checkManage(Caller caller, boolean targetIsClinicAdmin, Collection<UUID> targetClinicIds) {
        if (targetIsClinicAdmin && !caller.clinicAdmin()) {
            throw new AccessDeniedException("Klinik yöneticisinin hesabını yalnızca klinik yöneticisi değiştirebilir.");
        }
        if (caller.branchScoped() && targetClinicIds.stream().noneMatch(caller.clinicIds()::contains)) {
            throw new AccessDeniedException("Bu personel sizin şubelerinizde çalışmıyor.");
        }
    }
}
