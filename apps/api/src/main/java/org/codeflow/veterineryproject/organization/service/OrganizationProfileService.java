package org.codeflow.veterineryproject.organization.service;

import jakarta.persistence.EntityNotFoundException;
import org.codeflow.veterineryproject.common.audit.AuditRecorder;
import org.codeflow.veterineryproject.common.tenant.TenantContext;
import org.codeflow.veterineryproject.organization.dto.OrganizationDto.ProfileResponse;
import org.codeflow.veterineryproject.organization.dto.OrganizationDto.UpdateProfileRequest;
import org.codeflow.veterineryproject.organization.repository.OrganizationJdbcRepository;
import org.codeflow.veterineryproject.subscription.service.PlanLimitService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

/** Klinik (organizasyon) profili: ad, vergi ve iletişim bilgileri; salt okunur abonelik/kullanım özeti. */
@Service
public class OrganizationProfileService {

    private final OrganizationJdbcRepository repository;
    private final PlanLimitService planLimits;
    private final AuditRecorder audit;

    public OrganizationProfileService(OrganizationJdbcRepository repository, PlanLimitService planLimits, AuditRecorder audit) {
        this.repository = repository;
        this.planLimits = planLimits;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public ProfileResponse get() {
        UUID organizationId = TenantContext.require().organizationId();
        ProfileResponse profile = repository.findProfile(organizationId)
                .orElseThrow(() -> new EntityNotFoundException("Klinik bulunamadı."));
        return profile.with(repository.currentSubscription(organizationId).orElse(null), planLimits.usage());
    }

    @Transactional
    public ProfileResponse update(UpdateProfileRequest request) {
        TenantContext.ContextValues ctx = TenantContext.require();
        UpdateProfileRequest normalized = new UpdateProfileRequest(
                request.name().trim(),
                blankToNull(request.legalName()),
                blankToNull(request.taxOffice()),
                blankToNull(request.taxNumber()),
                blankToNull(request.phone()),
                request.email() == null || request.email().isBlank() ? null : request.email().trim().toLowerCase(Locale.ROOT),
                blankToNull(request.addressLine()));
        if (repository.updateProfile(ctx.organizationId(), normalized) == 0) {
            throw new EntityNotFoundException("Klinik bulunamadı.");
        }
        audit.record("ORGANIZATION_UPDATE", "organization", ctx.organizationId(), ctx.clinicId());
        return get();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
