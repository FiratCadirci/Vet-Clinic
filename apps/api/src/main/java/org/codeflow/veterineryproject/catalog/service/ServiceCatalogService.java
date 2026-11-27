package org.codeflow.veterineryproject.catalog.service;

import jakarta.persistence.EntityNotFoundException;
import org.codeflow.veterineryproject.catalog.dto.ServiceCatalogDto.ServiceRequest;
import org.codeflow.veterineryproject.catalog.dto.ServiceCatalogDto.ServiceResponse;
import org.codeflow.veterineryproject.catalog.repository.ServiceCatalogRepository;
import org.codeflow.veterineryproject.common.api.ConflictException;
import org.codeflow.veterineryproject.common.audit.AuditRecorder;
import org.codeflow.veterineryproject.common.tenant.TenantContext;
import org.codeflow.veterineryproject.common.text.CodeSlug;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Klinik hizmetleri (randevu süresi, satış fiyatı, KDV). Okuma tüm personele, yazma services.manage yetkisine açık. */
@Service
public class ServiceCatalogService {

    private static final String NOT_FOUND = "Hizmet bulunamadı.";

    private final ServiceCatalogRepository repository;
    private final AuditRecorder audit;

    public ServiceCatalogService(ServiceCatalogRepository repository, AuditRecorder audit) {
        this.repository = repository;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<ServiceResponse> list(boolean includeInactive) {
        return repository.list(includeInactive);
    }

    @Transactional(readOnly = true)
    public ServiceResponse get(UUID id) {
        return repository.find(id).orElseThrow(() -> new EntityNotFoundException(NOT_FOUND));
    }

    @Transactional
    public ServiceResponse create(ServiceRequest request) {
        TenantContext.ContextValues ctx = TenantContext.require();
        ServiceRequest r = normalize(request);
        UUID id = UUID.randomUUID();
        try {
            repository.insert(id, ctx.organizationId(), codeOf(r), r, r.active() == null || r.active());
        } catch (DuplicateKeyException ex) {
            throw duplicateCode();
        }
        audit.record("SERVICE_CREATE", "service_catalog", id, ctx.clinicId());
        return get(id);
    }

    @Transactional
    public ServiceResponse update(UUID id, ServiceRequest request) {
        TenantContext.ContextValues ctx = TenantContext.require();
        ServiceResponse existing = get(id);
        ServiceRequest r = normalize(request);
        String code = r.code() == null ? existing.code() : r.code();
        try {
            repository.update(id, code, r, r.active() == null ? existing.active() : r.active());
        } catch (DuplicateKeyException ex) {
            throw duplicateCode();
        }
        audit.record("SERVICE_UPDATE", "service_catalog", id, ctx.clinicId());
        return get(id);
    }

    /** Faturada kullanılmış hizmet silinemez (FK); pasife alınması önerilir. */
    @Transactional
    public void delete(UUID id) {
        TenantContext.ContextValues ctx = TenantContext.require();
        get(id);
        try {
            repository.delete(id);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("Hizmet satış kayıtlarında kullanıldığı için silinemez; pasife alabilirsiniz.",
                    "SERVICE_IN_USE", Map.of());
        }
        audit.record("SERVICE_DELETE", "service_catalog", id, ctx.clinicId());
    }

    private static String codeOf(ServiceRequest r) {
        return r.code() == null ? CodeSlug.of(r.name(), "HIZMET") : r.code();
    }

    private static ConflictException duplicateCode() {
        return new ConflictException("Bu ad veya kodla kayıtlı bir hizmet zaten var.", "DUPLICATE_SERVICE", Map.of());
    }

    private static ServiceRequest normalize(ServiceRequest r) {
        return new ServiceRequest(blankToNull(r.code()), r.name().trim(), blankToNull(r.category()), r.durationMinutes(),
                r.price(), r.taxRate(), r.active());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
