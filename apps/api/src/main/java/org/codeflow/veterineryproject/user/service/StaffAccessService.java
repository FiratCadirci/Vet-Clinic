package org.codeflow.veterineryproject.user.service;

import org.codeflow.veterineryproject.common.tenant.TenantContext;
import org.codeflow.veterineryproject.user.repository.StaffRoleRepository;
import org.codeflow.veterineryproject.user.repository.StaffRoleRepository.Assignment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Çağıranın güncel rolleri DB'den okunur (JWT'deki liste değil): geri alınan yetki hemen geçerli olur. */
@Service
public class StaffAccessService {

    private final StaffRoleRepository repository;

    public StaffAccessService(StaffRoleRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public RoleGrantPolicy.Caller currentCaller() {
        List<Assignment> assignments = repository.activeAssignments(TenantContext.require().userId());
        Set<String> codes = assignments.stream().map(Assignment::roleCode).collect(Collectors.toSet());
        return new RoleGrantPolicy.Caller(codes.contains(RoleGrantPolicy.CLINIC_ADMIN),
                codes.contains(RoleGrantPolicy.BRANCH_MANAGER), clinicIds(assignments));
    }

    public static boolean holdsClinicAdmin(List<Assignment> assignments) {
        return assignments.stream().anyMatch(a -> RoleGrantPolicy.CLINIC_ADMIN.equals(a.roleCode()));
    }

    public static Set<UUID> clinicIds(List<Assignment> assignments) {
        return assignments.stream().map(Assignment::clinicId).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public static Set<UUID> clinicAdminGroups(List<Assignment> assignments) {
        return assignments.stream()
                .filter(a -> RoleGrantPolicy.CLINIC_ADMIN.equals(a.roleCode()))
                .map(Assignment::clinicGroupId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
