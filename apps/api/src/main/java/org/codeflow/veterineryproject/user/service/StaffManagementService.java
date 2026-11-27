package org.codeflow.veterineryproject.user.service;

import jakarta.persistence.EntityNotFoundException;
import org.codeflow.veterineryproject.common.api.BusinessRuleException;
import org.codeflow.veterineryproject.common.api.ConflictException;
import org.codeflow.veterineryproject.common.audit.AuditRecorder;
import org.codeflow.veterineryproject.common.tenant.TenantContext;
import org.codeflow.veterineryproject.subscription.service.PlanLimitService;
import org.codeflow.veterineryproject.user.dto.ClinicRoleAssignmentRequest;
import org.codeflow.veterineryproject.user.dto.CreateUserRequest;
import org.codeflow.veterineryproject.user.dto.ReplaceClinicRolesRequest;
import org.codeflow.veterineryproject.user.dto.UserResponse;
import org.codeflow.veterineryproject.user.repository.StaffRoleRepository;
import org.codeflow.veterineryproject.user.repository.StaffRoleRepository.Assignment;
import org.codeflow.veterineryproject.user.repository.UserAdminRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Decorator: {@link UserService}'in kullanıcı yönetimi işlemlerini değiştirmeden FG-A5 (yönetici rolü verme, son
 * klinik yöneticisi), FG-A4 (şube yöneticisinin şube kapsamı) ve FG-K1 (kullanıcı limiti) kurallarıyla sarar.
 * Kontroller ve asıl işlem aynı transaction'da çalışır; organizasyon satırı kilidi eşzamanlı işlemleri sıraya sokar.
 */
@Service
public class StaffManagementService {

    static final String LAST_CLINIC_ADMIN = "LAST_CLINIC_ADMIN";
    private static final String LAST_CLINIC_ADMIN_MESSAGE = "Klinikte en az bir aktif klinik yöneticisi kalmalıdır.";

    private final UserService users;
    private final UserAdminRepository userRepository;
    private final StaffRoleRepository roles;
    private final StaffAccessService access;
    private final RoleGrantPolicy policy;
    private final PlanLimitService planLimits;
    private final AuditRecorder audit;

    public StaffManagementService(UserService users, UserAdminRepository userRepository, StaffRoleRepository roles,
                                  StaffAccessService access, RoleGrantPolicy policy, PlanLimitService planLimits,
                                  AuditRecorder audit) {
        this.users = users;
        this.userRepository = userRepository;
        this.roles = roles;
        this.access = access;
        this.policy = policy;
        this.planLimits = planLimits;
        this.audit = audit;
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        RoleGrantPolicy.Caller caller = access.currentCaller();
        for (ClinicRoleAssignmentRequest assignment : request.assignments()) {
            // Görünmeyen rol/şube UserService'te 422 ile reddedilir; burada yalnızca yetki verme kuralı uygulanır.
            roles.roleCode(assignment.roleId()).ifPresent(code -> policy.checkGrant(caller, code, List.of(assignment.clinicId())));
        }
        planLimits.assertCanAddUser();
        return users.create(request);
    }

    @Transactional
    public UserResponse addClinicRole(UUID id, ClinicRoleAssignmentRequest request) {
        RoleGrantPolicy.Caller caller = access.currentCaller();
        List<Assignment> current = requireTarget(id);
        policy.checkManage(caller, StaffAccessService.holdsClinicAdmin(current), StaffAccessService.clinicIds(current));
        roles.roleCode(request.roleId()).ifPresent(code -> policy.checkGrant(caller, code, List.of(request.clinicId())));
        return users.addClinicRole(id, request);
    }

    @Transactional
    public UserResponse endClinicRole(UUID id, UUID assignmentId) {
        TenantContext.ContextValues ctx = TenantContext.require();
        RoleGrantPolicy.Caller caller = access.currentCaller();
        List<Assignment> current = requireTarget(id);
        Assignment assignment = current.stream().filter(a -> a.id().equals(assignmentId)).findFirst().orElse(null);
        if (assignment != null) {
            policy.checkManage(caller, StaffAccessService.holdsClinicAdmin(current), StaffAccessService.clinicIds(current));
            policy.checkGrant(caller, assignment.roleCode(), List.of(assignment.clinicId()));
            if (RoleGrantPolicy.CLINIC_ADMIN.equals(assignment.roleCode())) {
                roles.lockOrganization(ctx.organizationId());
                if (roles.groupsWithoutClinicAdmin(List.of(assignment.clinicGroupId()), null, assignmentId) > 0) {
                    throw new ConflictException(LAST_CLINIC_ADMIN_MESSAGE, LAST_CLINIC_ADMIN, Map.of());
                }
            }
        }
        return users.endClinicRole(id, assignmentId);
    }

    @Transactional
    public UserResponse deactivate(UUID id) {
        TenantContext.ContextValues ctx = TenantContext.require();
        RoleGrantPolicy.Caller caller = access.currentCaller();
        List<Assignment> current = requireTarget(id);
        policy.checkManage(caller, StaffAccessService.holdsClinicAdmin(current), StaffAccessService.clinicIds(current));
        if (!ctx.userId().equals(id)) {
            roles.lockOrganization(ctx.organizationId());
            if (roles.groupsWithoutClinicAdmin(StaffAccessService.clinicAdminGroups(current), id, null) > 0) {
                throw new ConflictException(LAST_CLINIC_ADMIN_MESSAGE, LAST_CLINIC_ADMIN, Map.of());
            }
        }
        return users.deactivate(id);
    }

    @Transactional
    public UserResponse activate(UUID id) {
        RoleGrantPolicy.Caller caller = access.currentCaller();
        List<Assignment> current = requireTarget(id);
        policy.checkManage(caller, StaffAccessService.holdsClinicAdmin(current), StaffAccessService.clinicIds(current));
        UserResponse user = users.get(id);
        if (!user.active()) {
            planLimits.assertCanAddUser();
        }
        return users.activate(id);
    }

    /**
     * Personelin rolünü ve şubelerini değiştirir. Çağıranın yetki alanındaki (şube yöneticisi için kendi şubeleri)
     * eski atamalar hemen sonlandırılır, eksikler eklenir; alan dışındaki atamalar korunur.
     */
    @Transactional
    public UserResponse replaceClinicRoles(UUID id, ReplaceClinicRolesRequest request) {
        TenantContext.ContextValues ctx = TenantContext.require();
        if (ctx.userId().equals(id)) {
            throw new AccessDeniedException("Kendi rolünüzü değiştiremezsiniz.");
        }
        RoleGrantPolicy.Caller caller = access.currentCaller();
        List<Assignment> current = requireTarget(id);
        Set<UUID> clinicIds = new LinkedHashSet<>(request.clinicIds());
        for (UUID clinicId : clinicIds) {
            if (!userRepository.clinicVisible(clinicId)) {
                throw new BusinessRuleException("Şube, çağıranın klinik grubunda bulunmalıdır.");
            }
        }
        String roleCode = roles.roleCode(request.roleId()).orElseThrow(() -> new BusinessRuleException("Rol bulunamadı."));
        policy.checkManage(caller, StaffAccessService.holdsClinicAdmin(current), StaffAccessService.clinicIds(current));
        policy.checkGrant(caller, roleCode, clinicIds);

        roles.lockOrganization(ctx.organizationId());
        Set<UUID> adminGroupsBefore = StaffAccessService.clinicAdminGroups(current);
        for (Assignment assignment : current) {
            boolean inScope = !caller.branchScoped() || caller.clinicIds().contains(assignment.clinicId());
            boolean keep = clinicIds.contains(assignment.clinicId()) && assignment.roleId().equals(request.roleId());
            if (inScope && !keep) {
                roles.endImmediately(assignment.id());
            }
        }
        for (UUID clinicId : clinicIds) {
            boolean exists = current.stream().anyMatch(a -> a.clinicId().equals(clinicId) && a.roleId().equals(request.roleId()));
            if (!exists) {
                userRepository.insertAssignment(ctx.organizationId(), id, clinicId, request.roleId(), null, null, ctx.userId());
            }
        }
        if (roles.groupsWithoutClinicAdmin(adminGroupsBefore, null, null) > 0) {
            throw new ConflictException(LAST_CLINIC_ADMIN_MESSAGE, LAST_CLINIC_ADMIN, Map.of());
        }
        audit.record("USER_ROLES_REPLACE", "app_user", id, ctx.clinicId());
        return users.get(id);
    }

    private List<Assignment> requireTarget(UUID id) {
        if (userRepository.findById(id).isEmpty()) {
            throw new EntityNotFoundException("Kullanıcı bulunamadı.");
        }
        return roles.activeAssignments(id);
    }
}
