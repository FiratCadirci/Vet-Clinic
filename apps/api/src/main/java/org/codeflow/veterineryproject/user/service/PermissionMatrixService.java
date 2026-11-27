package org.codeflow.veterineryproject.user.service;

import org.codeflow.veterineryproject.user.dto.PermissionMatrixResponse;
import org.codeflow.veterineryproject.user.dto.PermissionMatrixResponse.PermissionItem;
import org.codeflow.veterineryproject.user.dto.RoleResponse;
import org.codeflow.veterineryproject.user.repository.StaffRoleRepository;
import org.codeflow.veterineryproject.user.repository.UserAdminRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/** FG-J3: roller × yetkiler matrisi. Rol adları ve yetki açıklamaları DB'deki Türkçe kayıtlardan gelir (V1, V22). */
@Service
public class PermissionMatrixService {

    /** Sistem rolleri dokümandaki sırayla, ardından kliniğe özel roller ada göre listelenir. */
    private static final List<String> SYSTEM_ROLE_ORDER = List.of(
            RoleGrantPolicy.CLINIC_ADMIN, RoleGrantPolicy.BRANCH_MANAGER, "veterinarian", "receptionist");

    private final UserAdminRepository roles;
    private final StaffRoleRepository staffRoles;

    public PermissionMatrixService(UserAdminRepository roles, StaffRoleRepository staffRoles) {
        this.roles = roles;
        this.staffRoles = staffRoles;
    }

    @Transactional(readOnly = true)
    public PermissionMatrixResponse matrix() {
        List<PermissionItem> permissions = staffRoles.permissions();
        List<RoleResponse> ordered = roles.findRoles().stream()
                .sorted(Comparator.comparingInt((RoleResponse r) -> rank(r.code())).thenComparing(RoleResponse::name))
                .toList();
        return new PermissionMatrixResponse(permissions, ordered);
    }

    private static int rank(String code) {
        int index = SYSTEM_ROLE_ORDER.indexOf(code);
        return index < 0 ? SYSTEM_ROLE_ORDER.size() : index;
    }
}
