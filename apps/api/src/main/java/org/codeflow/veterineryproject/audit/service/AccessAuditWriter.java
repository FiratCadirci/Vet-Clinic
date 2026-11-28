package org.codeflow.veterineryproject.audit.service;

import org.codeflow.veterineryproject.audit.model.AccessAuditEntry;
import org.codeflow.veterineryproject.audit.repository.AuditLogJdbcRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Görüntüleme kayıtlarını kendi transaction'ında yazar (FG-J4). Okuma uçlarının transaction'ı readOnly olduğu ve
 * istek bittiğinde kapandığı için kayıt ayrı (REQUIRES_NEW) bir transaction'da, tenant bağlamı yeniden set edilerek
 * yapılır. Kritik değişiklikler ise işlemle aynı transaction'da AuditRecorder ile yazılmaya devam eder.
 */
@Component
public class AccessAuditWriter {

    private final AuditLogJdbcRepository repository;

    public AccessAuditWriter(AuditLogJdbcRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(AccessAuditEntry entry) {
        repository.insertAccess(entry);
    }
}
