package org.codeflow.veterineryproject.common.audit;

import org.codeflow.veterineryproject.common.tenant.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Kritik işlemler için denetim izi: kim, ne, ne zaman, kayıt id. Kişisel veri veya tıbbi içerik yazılmaz. */
@Component
public class AuditRecorder {

    private final JdbcTemplate jdbc;

    public AuditRecorder(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void record(String action, String entityType, UUID entityId, UUID clinicId) {
        TenantContext.ContextValues ctx = TenantContext.require();
        jdbc.update("""
                insert into vet.audit_logs (organization_id, clinic_id, actor_user_id, action, entity_type, entity_id)
                values (?, ?, ?, ?, ?, ?)
                """, ctx.organizationId(), clinicId, ctx.userId(), action, entityType, entityId);
    }
}
