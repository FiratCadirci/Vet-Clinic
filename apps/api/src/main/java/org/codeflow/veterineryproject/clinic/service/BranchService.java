package org.codeflow.veterineryproject.clinic.service;

import jakarta.persistence.EntityNotFoundException;
import org.codeflow.veterineryproject.clinic.dto.BranchDto.BranchRequest;
import org.codeflow.veterineryproject.clinic.dto.BranchDto.BranchResponse;
import org.codeflow.veterineryproject.clinic.repository.BranchJdbcRepository;
import org.codeflow.veterineryproject.common.audit.AuditRecorder;
import org.codeflow.veterineryproject.common.tenant.TenantContext;
import org.codeflow.veterineryproject.common.text.CodeSlug;
import org.codeflow.veterineryproject.subscription.service.PlanLimitService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

/**
 * Şube oluşturma, güncelleme ve aktif/pasif. Yeni şube, işlemin yapıldığı şubenin (X-Clinic-Id) klinik grubunda açılır.
 * Yeni şube ve pasif şubenin yeniden açılması paketin aktif şube limitine tabidir (FG-K1).
 */
@Service
public class BranchService {

    private static final String NOT_FOUND = "Şube bulunamadı.";

    private final BranchJdbcRepository repository;
    private final PlanLimitService planLimits;
    private final AuditRecorder audit;

    public BranchService(BranchJdbcRepository repository, PlanLimitService planLimits, AuditRecorder audit) {
        this.repository = repository;
        this.planLimits = planLimits;
        this.audit = audit;
    }

    @Transactional
    public BranchResponse create(BranchRequest request) {
        TenantContext.ContextValues ctx = TenantContext.require();
        BranchResponse current = repository.find(ctx.clinicId()).orElseThrow(() -> new EntityNotFoundException(NOT_FOUND));
        planLimits.assertCanAddBranch();
        BranchRequest normalized = normalize(request);
        UUID id = UUID.randomUUID();
        repository.insert(id, ctx.organizationId(), current.clinicGroupId(), CodeSlug.withRandomSuffix(normalized.name(), "SUBE"), normalized);
        repository.copyActiveAssignments(ctx.organizationId(), ctx.userId(), ctx.clinicId(), id);
        audit.record("BRANCH_CREATE", "clinic", id, ctx.clinicId());
        return load(id);
    }

    @Transactional
    public BranchResponse update(UUID id, BranchRequest request) {
        TenantContext.ContextValues ctx = TenantContext.require();
        if (repository.update(id, normalize(request)) == 0) {
            throw new EntityNotFoundException(NOT_FOUND);
        }
        audit.record("BRANCH_UPDATE", "clinic", id, ctx.clinicId());
        return load(id);
    }

    @Transactional
    public BranchResponse deactivate(UUID id) {
        TenantContext.ContextValues ctx = TenantContext.require();
        BranchResponse branch = load(id);
        if (branch.active()) {
            repository.setActive(id, false);
            audit.record("BRANCH_DEACTIVATE", "clinic", id, ctx.clinicId());
        }
        return load(id);
    }

    @Transactional
    public BranchResponse activate(UUID id) {
        TenantContext.ContextValues ctx = TenantContext.require();
        BranchResponse branch = load(id);
        if (!branch.active()) {
            planLimits.assertCanAddBranch();
            repository.setActive(id, true);
            audit.record("BRANCH_ACTIVATE", "clinic", id, ctx.clinicId());
        }
        return load(id);
    }

    private BranchResponse load(UUID id) {
        return repository.find(id).orElseThrow(() -> new EntityNotFoundException(NOT_FOUND));
    }

    private static BranchRequest normalize(BranchRequest r) {
        return new BranchRequest(r.name().trim(), blankToNull(r.phone()),
                r.email() == null || r.email().isBlank() ? null : r.email().trim().toLowerCase(Locale.ROOT),
                blankToNull(r.city()), blankToNull(r.district()), blankToNull(r.addressLine()), blankToNull(r.postalCode()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
