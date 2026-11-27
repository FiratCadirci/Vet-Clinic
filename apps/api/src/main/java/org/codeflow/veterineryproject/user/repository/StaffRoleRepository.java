package org.codeflow.veterineryproject.user.repository;

import org.codeflow.veterineryproject.user.dto.PermissionMatrixResponse.PermissionItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Personel yönetimi kuralları için rol/şube sorguları. RLS altında çalışır: yalnızca çağıranın klinik grubundaki
 * atamalar görünür. "Klinik" = klinik grubu; son klinik yöneticisi kuralı grup bazındadır.
 */
@Repository
public class StaffRoleRepository {

    private static final String ACTIVE = "ucr.valid_from <= current_date and (ucr.valid_to is null or ucr.valid_to >= current_date)";

    /** Bir kullanıcının geçerli şube rol ataması. */
    public record Assignment(UUID id, UUID clinicId, UUID clinicGroupId, UUID roleId, String roleCode, LocalDate validFrom) {
    }

    private final JdbcTemplate jdbc;

    public StaffRoleRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Assignment> activeAssignments(UUID userId) {
        return jdbc.query("""
                select ucr.id, ucr.clinic_id, c.clinic_group_id, ucr.role_id, r.code, ucr.valid_from
                from vet.user_clinic_roles ucr
                join vet.clinics c on c.organization_id = ucr.organization_id and c.id = ucr.clinic_id
                join vet.roles r on r.organization_id = ucr.organization_id and r.id = ucr.role_id
                where ucr.user_id = ? and %s
                order by ucr.valid_from, ucr.id
                """.formatted(ACTIVE), (rs, i) -> new Assignment(
                rs.getObject("id", UUID.class),
                rs.getObject("clinic_id", UUID.class),
                rs.getObject("clinic_group_id", UUID.class),
                rs.getObject("role_id", UUID.class),
                rs.getString("code"),
                rs.getObject("valid_from", LocalDate.class)
        ), userId);
    }

    public Optional<String> roleCode(UUID roleId) {
        return jdbc.queryForList("select code from vet.roles where id = ?", String.class, roleId).stream().findFirst();
    }

    /** Yetki kataloğu platform geneli referans tablosudur (RLS yok). */
    public List<PermissionItem> permissions() {
        return jdbc.query("select code, description from vet.permissions order by code",
                (rs, i) -> new PermissionItem(rs.getString("code"), rs.getString("description")));
    }

    /** Organizasyon satırını kilitler; yönetici sayımı ve paket limiti kontrolleri sıraya girer. */
    public void lockOrganization(UUID organizationId) {
        jdbc.queryForList("select id from vet.organizations where id = ? for update", UUID.class, organizationId);
    }

    /**
     * Verilen klinik gruplarından, hariç tutulan kullanıcı/atama dışında aktif klinik yöneticisi kalmayanların sayısı.
     * Bugün sona eren atamalar (valid_to = bugün; UserService.endClinicRole/deactivate böyle kapatır) yönetici sayılmaz.
     */
    public int groupsWithoutClinicAdmin(Collection<UUID> groupIds, UUID excludedUserId, UUID excludedAssignmentId) {
        Set<UUID> groups = new LinkedHashSet<>(groupIds);
        if (groups.isEmpty()) {
            return 0;
        }
        String placeholders = String.join(",", groups.stream().map(g -> "?::uuid").toList());
        List<Object> args = new ArrayList<>(groups);
        args.add(excludedUserId);
        args.add(excludedUserId);
        args.add(excludedAssignmentId);
        args.add(excludedAssignmentId);
        Integer count = jdbc.queryForObject("""
                select count(*) from unnest(array[%s]) as g(id)
                where not exists (
                    select 1
                    from vet.user_clinic_roles ucr
                    join vet.app_users u on u.organization_id = ucr.organization_id and u.id = ucr.user_id
                    join vet.clinics c on c.organization_id = ucr.organization_id and c.id = ucr.clinic_id
                    join vet.roles r on r.organization_id = ucr.organization_id and r.id = ucr.role_id
                    where c.clinic_group_id = g.id
                      and r.code = 'clinic_admin'
                      and u.is_active
                      and ucr.valid_from <= current_date and (ucr.valid_to is null or ucr.valid_to > current_date)
                      and (?::uuid is null or ucr.user_id <> ?::uuid)
                      and (?::uuid is null or ucr.id <> ?::uuid)
                )
                """.formatted(placeholders), Integer.class, args.toArray());
        return count == null ? 0 : count;
    }

    /** Atamayı hemen sonlandırır: geçmişte başlamışsa dünle kapatılır, bugün başlamışsa silinir. */
    public void endImmediately(UUID assignmentId) {
        int closed = jdbc.update("""
                update vet.user_clinic_roles set valid_to = current_date - 1
                where id = ? and valid_from < current_date
                """, assignmentId);
        if (closed == 0) {
            jdbc.update("delete from vet.user_clinic_roles where id = ?", assignmentId);
        }
    }
}
