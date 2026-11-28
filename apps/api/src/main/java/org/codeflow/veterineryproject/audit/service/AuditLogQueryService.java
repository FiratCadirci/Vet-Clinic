package org.codeflow.veterineryproject.audit.service;

import org.codeflow.veterineryproject.audit.dto.AuditLogResponse;
import org.codeflow.veterineryproject.audit.model.AuditLogFilter;
import org.codeflow.veterineryproject.audit.repository.AuditLogJdbcRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Denetim izi ekranı (FG-L2): filtrelenebilir, en fazla 500 kayıt, kullanıcının şubeleriyle sınırlı. */
@Service
public class AuditLogQueryService {

    private final AuditLogJdbcRepository repository;

    public AuditLogQueryService(AuditLogJdbcRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<AuditLogResponse> search(AuditLogFilter filter, Collection<UUID> userClinicIds) {
        return repository.search(filter, userClinicIds == null ? List.of() : userClinicIds);
    }
}
