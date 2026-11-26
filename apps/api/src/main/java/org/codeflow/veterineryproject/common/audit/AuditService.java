package org.codeflow.veterineryproject.common.audit;

import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public AuditLog log(AuditLogCreateRequest request) {
        AuditLog log = new AuditLog();
        log.setOrganizationId(request.organizationId());
        log.setClinicId(request.clinicId());
        log.setActorUserId(request.actorUserId());
        log.setAction(request.action());
        log.setEntityType(request.entityType());
        log.setEntityId(request.entityId());
        log.setRequestId(request.requestId());
        log.setIpAddress(request.ipAddress());
        log.setUserAgent(request.userAgent());
        log.setOldValues(request.oldValues());
        log.setNewValues(request.newValues());
        log.setReason(request.reason());
        return auditLogRepository.save(log);
    }
}

