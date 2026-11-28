package org.codeflow.veterineryproject.audit.repository;

import org.codeflow.veterineryproject.audit.dto.AuditLogResponse;
import org.codeflow.veterineryproject.audit.model.AccessAuditEntry;
import org.codeflow.veterineryproject.audit.model.AuditLogFilter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * vet.audit_logs okuma (FG-L2) ve kayıt görüntüleme yazımı (FG-J4). Tablo immutable'dır (tetikleyici);
 * organizasyon sınırı RLS'te, şube sınırı sorguda uygulanır.
 */
@Repository
public class AuditLogJdbcRepository {

    private final JdbcTemplate jdbc;

    public AuditLogJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * En yeni kayıttan başlayarak filtreye uyan kayıtlar. Yalnız kullanıcının şubelerine ait ya da şubesiz
     * (organizasyon düzeyi) kayıtlar döner.
     */
    public List<AuditLogResponse> search(AuditLogFilter f, Collection<UUID> clinics) {
        StringBuilder sql = new StringBuilder("""
                select a.id, a.occurred_at, a.actor_user_id, u.display_name as user_name, a.action, a.entity_type,
                       a.entity_id, a.clinic_id, c.name as clinic_name, host(a.ip_address) as ip_address
                from vet.audit_logs a
                left join vet.app_users u on u.organization_id = a.organization_id and u.id = a.actor_user_id
                left join vet.clinics c on c.organization_id = a.organization_id and c.id = a.clinic_id
                where (a.clinic_id is null or a.clinic_id = any(?::uuid[]))
                """);
        List<Object> args = new ArrayList<>();
        args.add(clinics.stream().map(UUID::toString).collect(Collectors.joining(",", "{", "}")));
        if (f.action() != null) {
            sql.append(" and upper(a.action) = ?");
            args.add(f.action());
        }
        if (f.entityType() != null) {
            sql.append(" and upper(a.entity_type) = ?");
            args.add(f.entityType());
        }
        if (f.entityId() != null) {
            sql.append(" and a.entity_id = ?");
            args.add(f.entityId());
        }
        if (f.userId() != null) {
            sql.append(" and a.actor_user_id = ?");
            args.add(f.userId());
        }
        if (f.from() != null) {
            sql.append(" and a.occurred_at >= ?");
            args.add(f.from());
        }
        if (f.to() != null) {
            sql.append(" and a.occurred_at < ?");
            args.add(f.to());
        }
        sql.append(" order by a.occurred_at desc, a.id desc limit ?");
        args.add(f.limit());
        return jdbc.query(sql.toString(), (rs, n) -> new AuditLogResponse(rs.getLong("id"),
                rs.getObject("occurred_at", OffsetDateTime.class), rs.getObject("actor_user_id", UUID.class),
                rs.getString("user_name"), rs.getString("action"), rs.getString("entity_type"),
                rs.getObject("entity_id", UUID.class), rs.getObject("clinic_id", UUID.class),
                rs.getString("clinic_name"), rs.getString("ip_address")), args.toArray());
    }

    public void insertAccess(AccessAuditEntry e) {
        jdbc.update("""
                insert into vet.audit_logs (organization_id, clinic_id, actor_user_id, action, entity_type, entity_id,
                                            request_id, ip_address, user_agent)
                values (?, ?, ?, ?, ?, ?, ?, ?::inet, ?)
                """, e.organizationId(), e.clinicId(), e.userId(), e.action(), e.entityType(), e.entityId(),
                e.requestId(), e.ipAddress(), e.userAgent());
    }
}
