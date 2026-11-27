package org.codeflow.veterineryproject.staff.service;

import jakarta.persistence.EntityNotFoundException;
import org.codeflow.veterineryproject.auth.repository.AuthReadRepository;
import org.codeflow.veterineryproject.common.api.BusinessRuleException;
import org.codeflow.veterineryproject.common.api.ConflictException;
import org.codeflow.veterineryproject.common.audit.AuditRecorder;
import org.codeflow.veterineryproject.common.event.DomainEvents.StaffInvitedEvent;
import org.codeflow.veterineryproject.common.tenant.TenantContext;
import org.codeflow.veterineryproject.staff.dto.StaffInvitationDto.CreateRequest;
import org.codeflow.veterineryproject.staff.dto.StaffInvitationDto.InvitationResponse;
import org.codeflow.veterineryproject.staff.repository.StaffInvitationRepository;
import org.codeflow.veterineryproject.staff.repository.StaffInvitationRepository.InvitationRow;
import org.codeflow.veterineryproject.subscription.service.PlanLimitService;
import org.codeflow.veterineryproject.user.repository.StaffRoleRepository;
import org.codeflow.veterineryproject.user.service.RoleGrantPolicy;
import org.codeflow.veterineryproject.user.service.StaffAccessService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * FG-A2 personel daveti (users.manage). Davet oluşunca {@link StaffInvitedEvent} yayınlanır (Observer); e-postayı
 * bildirim modülü commit sonrasında gönderir. Ham token yalnızca olayda taşınır, DB'de özeti tutulur.
 */
@Service
public class StaffInvitationService {

    /** Proje dokümanı: davet bağlantısı 7 gün geçerlidir. */
    public static final Duration VALIDITY = Duration.ofDays(7);

    private final StaffInvitationRepository repository;
    private final StaffRoleRepository roles;
    private final StaffAccessService access;
    private final RoleGrantPolicy policy;
    private final PlanLimitService planLimits;
    private final AuthReadRepository authReadRepository;
    private final AuditRecorder audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public StaffInvitationService(StaffInvitationRepository repository, StaffRoleRepository roles, StaffAccessService access,
                                  RoleGrantPolicy policy, PlanLimitService planLimits, AuthReadRepository authReadRepository,
                                  AuditRecorder audit, ApplicationEventPublisher events) {
        this.repository = repository;
        this.roles = roles;
        this.access = access;
        this.policy = policy;
        this.planLimits = planLimits;
        this.authReadRepository = authReadRepository;
        this.audit = audit;
        this.events = events;
        this.clock = Clock.systemUTC();
    }

    @Transactional
    public InvitationResponse create(CreateRequest request) {
        TenantContext.ContextValues ctx = TenantContext.require();
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        Set<UUID> clinicIds = new LinkedHashSet<>(request.clinicIds());
        for (UUID clinicId : clinicIds) {
            boolean active = repository.clinicActive(clinicId)
                    .orElseThrow(() -> new BusinessRuleException("Şube, çağıranın klinik grubunda bulunmalıdır."));
            if (!active) {
                throw new BusinessRuleException("Pasif şubeye personel davet edilemez.");
            }
        }
        String roleCode = roles.roleCode(request.roleId()).orElseThrow(() -> new BusinessRuleException("Rol bulunamadı."));
        policy.checkGrant(access.currentCaller(), roleCode, clinicIds);

        if (authReadRepository.resolveLoginUserId(ctx.organizationId(), email) != null) {
            throw new ConflictException("Bu e-posta adresi ile kayıtlı bir kullanıcı zaten var.", "EMAIL_TAKEN", Map.of());
        }
        planLimits.assertCanAddUser();
        repository.revokePendingFor(email, ctx.userId());

        InvitationToken token = InvitationToken.generate();
        UUID id = UUID.randomUUID();
        repository.insert(id, ctx.organizationId(), email, request.roleId(), clinicIds, token.hash(), ctx.userId(),
                OffsetDateTime.now(clock).plus(VALIDITY));
        audit.record("STAFF_INVITATION_CREATE", "staff_invitation", id, ctx.clinicId());
        events.publishEvent(new StaffInvitedEvent(ctx.organizationId(), id, email, token.raw(),
                repository.organizationName(ctx.organizationId()), ctx.userId()));
        return toResponse(load(id));
    }

    /** Şube yöneticisi yalnızca kendi şubelerinden birini içeren davetleri görür. */
    @Transactional(readOnly = true)
    public List<InvitationResponse> list(String status) {
        RoleGrantPolicy.Caller caller = access.currentCaller();
        return repository.list().stream()
                .filter(row -> !caller.branchScoped() || row.clinicIds().stream().anyMatch(caller.clinicIds()::contains))
                .map(this::toResponse)
                .filter(r -> status == null || status.isBlank() || r.status().equalsIgnoreCase(status))
                .toList();
    }

    @Transactional
    public InvitationResponse revoke(UUID id) {
        TenantContext.ContextValues ctx = TenantContext.require();
        InvitationRow row = load(id);
        policy.checkGrant(access.currentCaller(), row.roleCode(), row.clinicIds());
        status(row).requirePending();
        repository.revoke(id, ctx.userId());
        audit.record("STAFF_INVITATION_REVOKE", "staff_invitation", id, ctx.clinicId());
        return toResponse(load(id));
    }

    private InvitationRow load(UUID id) {
        return repository.find(id).orElseThrow(() -> new EntityNotFoundException("Davet bulunamadı."));
    }

    private InvitationStatus status(InvitationRow row) {
        return InvitationStatus.of(row.acceptedAt(), row.revokedAt(), row.expiresAt(), OffsetDateTime.now(clock));
    }

    private InvitationResponse toResponse(InvitationRow row) {
        return new InvitationResponse(row.id(), row.email(), row.roleId(), row.roleCode(), row.roleName(), row.clinicIds(),
                status(row).name(), row.invitedBy(), row.invitedByName(), row.expiresAt(), row.acceptedAt(),
                row.revokedAt(), row.createdAt());
    }
}
