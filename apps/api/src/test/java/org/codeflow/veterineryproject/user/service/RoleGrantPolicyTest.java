package org.codeflow.veterineryproject.user.service;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** FG-A5 / FG-A4 rol verme politikası (DB'siz). */
class RoleGrantPolicyTest {

    private final RoleGrantPolicy policy = new RoleGrantPolicy();
    private final UUID own = UUID.randomUUID();
    private final UUID foreign = UUID.randomUUID();
    private final RoleGrantPolicy.Caller admin = new RoleGrantPolicy.Caller(true, false, Set.of(own));
    private final RoleGrantPolicy.Caller manager = new RoleGrantPolicy.Caller(false, true, Set.of(own));
    private final RoleGrantPolicy.Caller customManager = new RoleGrantPolicy.Caller(false, false, Set.of(own));

    @Test
    void onlyClinicAdminGrantsClinicAdminRole() {
        assertThatCode(() -> policy.checkGrant(admin, "clinic_admin", List.of(own, foreign))).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.checkGrant(manager, "clinic_admin", List.of(own))).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> policy.checkGrant(customManager, "clinic_admin", List.of(own))).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void branchManagerAssignsOnlyToOwnBranches() {
        assertThatCode(() -> policy.checkGrant(manager, "veterinarian", List.of(own))).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.checkGrant(manager, "veterinarian", List.of(own, foreign)))
                .isInstanceOf(AccessDeniedException.class);
        // Şube yöneticisi olmayan, users.manage'li özel roller klinik grubunun tamamında çalışır.
        assertThatCode(() -> policy.checkGrant(customManager, "veterinarian", List.of(foreign))).doesNotThrowAnyException();
    }

    @Test
    void managingClinicAdminsAndOtherBranchStaffIsRestricted() {
        assertThatThrownBy(() -> policy.checkManage(manager, true, List.of(own))).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> policy.checkManage(manager, false, List.of(foreign))).isInstanceOf(AccessDeniedException.class);
        assertThatCode(() -> policy.checkManage(manager, false, List.of(own, foreign))).doesNotThrowAnyException();
        assertThatCode(() -> policy.checkManage(admin, true, List.of(foreign))).doesNotThrowAnyException();
    }
}
