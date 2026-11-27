package org.codeflow.veterineryproject.staff.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Personel davetleri. Oturum içi sorgular RLS altındadır (en az bir şubesi çağıranın klinik grubunda olan davetler);
 * oturum öncesi önizleme/kabul SECURITY DEFINER fonksiyonlarla yapılır (V23).
 */
@Repository
public class StaffInvitationRepository {

    public record InvitationRow(UUID id, String email, UUID roleId, String roleCode, String roleName, List<UUID> clinicIds,
                                UUID invitedBy, String invitedByName, OffsetDateTime expiresAt, OffsetDateTime acceptedAt,
                                OffsetDateTime revokedAt, OffsetDateTime createdAt) {
    }

    public record PreviewRow(UUID invitationId, String organizationName, String email, String roleCode, String roleName,
                             OffsetDateTime expiresAt, String status) {
    }

    public record AcceptResult(String result, UUID organizationId, UUID userId, Integer maxAllowed) {
    }

    private static final RowMapper<InvitationRow> MAPPER = (rs, i) -> new InvitationRow(
            rs.getObject("id", UUID.class),
            rs.getString("email"),
            rs.getObject("role_id", UUID.class),
            rs.getString("role_code"),
            rs.getString("role_name"),
            uuids(rs.getArray("clinic_ids")),
            rs.getObject("invited_by", UUID.class),
            rs.getString("invited_by_name"),
            rs.getObject("expires_at", OffsetDateTime.class),
            rs.getObject("accepted_at", OffsetDateTime.class),
            rs.getObject("revoked_at", OffsetDateTime.class),
            rs.getObject("created_at", OffsetDateTime.class)
    );

    private static final String SELECT = """
            select i.id, i.email, i.role_id, r.code as role_code, r.name as role_name, i.clinic_ids, i.invited_by,
                   u.display_name as invited_by_name, i.expires_at, i.accepted_at, i.revoked_at, i.created_at
            from vet.staff_invitations i
            join vet.roles r on r.organization_id = i.organization_id and r.id = i.role_id
            left join vet.app_users u on u.organization_id = i.organization_id and u.id = i.invited_by
            """;

    private final JdbcTemplate jdbc;

    public StaffInvitationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(UUID id, UUID organizationId, String email, UUID roleId, Collection<UUID> clinicIds,
                       String tokenHash, UUID invitedBy, OffsetDateTime expiresAt) {
        jdbc.update("""
                insert into vet.staff_invitations (id, organization_id, email, role_id, clinic_ids, token_hash, invited_by, expires_at)
                values (?, ?, ?, ?, ?::uuid[], ?, ?, ?)
                """, id, organizationId, email, roleId, pgArray(clinicIds), tokenHash, invitedBy, expiresAt);
    }

    public Optional<InvitationRow> find(UUID id) {
        return jdbc.query(SELECT + " where i.id = ?", MAPPER, id).stream().findFirst();
    }

    public List<InvitationRow> list() {
        return jdbc.query(SELECT + " order by i.created_at desc, i.id limit 500", MAPPER);
    }

    /** Aynı adrese yeni davet gönderilince bekleyen eski davetler iptal edilir (tek geçerli bağlantı). */
    public int revokePendingFor(String email, UUID revokedBy) {
        return jdbc.update("""
                update vet.staff_invitations set revoked_at = now(), revoked_by = ?
                where lower(email) = lower(?) and accepted_at is null and revoked_at is null
                """, revokedBy, email);
    }

    public int revoke(UUID id, UUID revokedBy) {
        return jdbc.update("""
                update vet.staff_invitations set revoked_at = now(), revoked_by = ?
                where id = ? and accepted_at is null and revoked_at is null
                """, revokedBy, id);
    }

    public Optional<Boolean> clinicActive(UUID clinicId) {
        return jdbc.queryForList("select is_active from vet.clinics where id = ?", Boolean.class, clinicId).stream().findFirst();
    }

    public String organizationName(UUID organizationId) {
        return jdbc.queryForList("select name from vet.organizations where id = ?", String.class, organizationId)
                .stream().findFirst().orElse("");
    }

    public Optional<PreviewRow> preview(String tokenHash) {
        return jdbc.query("select * from vet.find_staff_invitation(?)", (rs, i) -> new PreviewRow(
                rs.getObject("invitation_id", UUID.class),
                rs.getString("organization_name"),
                rs.getString("email"),
                rs.getString("role_code"),
                rs.getString("role_name"),
                rs.getObject("expires_at", OffsetDateTime.class),
                rs.getString("status")
        ), tokenHash).stream().findFirst();
    }

    public AcceptResult accept(String tokenHash, String displayName, String passwordHash) {
        return jdbc.queryForObject("select * from vet.accept_staff_invitation(?, ?, ?)", (rs, i) -> new AcceptResult(
                rs.getString("result"),
                rs.getObject("organization_id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getObject("max_allowed", Integer.class)
        ), tokenHash, displayName, passwordHash);
    }

    private static String pgArray(Collection<UUID> ids) {
        return ids.stream().map(UUID::toString).collect(Collectors.joining(",", "{", "}"));
    }

    private static List<UUID> uuids(Array array) throws SQLException {
        if (array == null) {
            return List.of();
        }
        Object[] values = (Object[]) array.getArray();
        return Arrays.stream(values).map(v -> v instanceof UUID u ? u : UUID.fromString(v.toString())).toList();
    }
}
