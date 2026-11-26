package org.codeflow.veterineryproject.user.repository;

import org.codeflow.veterineryproject.user.dto.ClinicRoleResponse;
import org.codeflow.veterineryproject.user.dto.RoleResponse;
import org.codeflow.veterineryproject.user.dto.UserResponse;
import org.codeflow.veterineryproject.common.sql.SqlLike;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Tüm sorgular RLS altında çalışır: yalnızca çağıranın klinik grubundaki kullanıcılar/atamalar görünür. */
@Repository
public class UserAdminRepository {

    private static final String ACTIVE_ROLE_PREDICATE =
            "ucr.valid_from <= current_date and (ucr.valid_to is null or ucr.valid_to >= current_date)";

    private static final RowMapper<UserResponse> USER_MAPPER = (rs, i) -> new UserResponse(
            rs.getObject("id", UUID.class),
            rs.getString("email"),
            rs.getString("display_name"),
            rs.getString("phone"),
            rs.getBoolean("is_active"),
            rs.getBoolean("must_change_password"),
            rs.getObject("last_login_at", OffsetDateTime.class),
            rs.getObject("created_at", OffsetDateTime.class),
            List.of()
    );

    private final JdbcTemplate jdbc;

    public UserAdminRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insertUser(UUID id, UUID organizationId, String email, String passwordHash, String displayName, String phone) {
        jdbc.update("""
                insert into vet.app_users (id, organization_id, email, password_hash, display_name, phone, is_active, must_change_password)
                values (?, ?, ?, ?, ?, ?, true, true)
                """, id, organizationId, email, passwordHash, displayName, phone);
    }

    public UUID insertAssignment(UUID organizationId, UUID userId, UUID clinicId, UUID roleId,
                                 LocalDate validFrom, LocalDate validTo, UUID assignedBy) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into vet.user_clinic_roles (id, organization_id, user_id, clinic_id, role_id, valid_from, valid_to, assigned_by)
                values (?, ?, ?, ?, ?, coalesce(?, current_date), ?, ?)
                """, id, organizationId, userId, clinicId, roleId,
                validFrom == null ? null : Date.valueOf(validFrom),
                validTo == null ? null : Date.valueOf(validTo),
                assignedBy);
        return id;
    }

    public Optional<UserResponse> findById(UUID id) {
        List<UserResponse> rows = jdbc.query(userSelect() + " where u.id = ?", USER_MAPPER, id);
        return rows.stream().findFirst();
    }

    public List<UserResponse> search(String query, Boolean active, int limit, long offset) {
        List<Object> args = new ArrayList<>();
        String where = searchWhere(query, active, args);
        args.add(limit);
        args.add(offset);
        return jdbc.query(userSelect() + where + " order by lower(u.display_name), u.id limit ? offset ?",
                USER_MAPPER, args.toArray());
    }

    public long count(String query, Boolean active) {
        List<Object> args = new ArrayList<>();
        String where = searchWhere(query, active, args);
        Long total = jdbc.queryForObject("select count(*) from vet.app_users u" + where, Long.class, args.toArray());
        return total == null ? 0 : total;
    }

    public Map<UUID, List<ClinicRoleResponse>> findAssignments(Collection<UUID> userIds) {
        Map<UUID, List<ClinicRoleResponse>> result = new LinkedHashMap<>();
        if (userIds.isEmpty()) {
            return result;
        }
        String placeholders = String.join(",", userIds.stream().map(x -> "?").toList());
        jdbc.query("""
                select ucr.user_id, ucr.id, ucr.clinic_id, c.name as clinic_name, ucr.role_id,
                       r.code as role_code, r.name as role_name, ucr.valid_from, ucr.valid_to
                from vet.user_clinic_roles ucr
                join vet.clinics c on c.organization_id = ucr.organization_id and c.id = ucr.clinic_id
                join vet.roles r on r.organization_id = ucr.organization_id and r.id = ucr.role_id
                where ucr.user_id in (%s)
                order by ucr.valid_from, ucr.created_at, ucr.id
                """.formatted(placeholders), rs -> {
            result.computeIfAbsent(rs.getObject("user_id", UUID.class), k -> new ArrayList<>()).add(new ClinicRoleResponse(
                    rs.getObject("id", UUID.class),
                    rs.getObject("clinic_id", UUID.class),
                    rs.getString("clinic_name"),
                    rs.getObject("role_id", UUID.class),
                    rs.getString("role_code"),
                    rs.getString("role_name"),
                    rs.getObject("valid_from", LocalDate.class),
                    rs.getObject("valid_to", LocalDate.class)
            ));
        }, userIds.toArray());
        return result;
    }

    public boolean clinicVisible(UUID clinicId) {
        return exists("select 1 from vet.clinics where id = ?", clinicId);
    }

    public boolean roleVisible(UUID roleId) {
        return exists("select 1 from vet.roles where id = ?", roleId);
    }

    public int updateProfile(UUID id, String displayName, String phone, boolean phoneProvided) {
        return jdbc.update("""
                update vet.app_users
                set display_name = coalesce(?, display_name),
                    phone = case when ? then ? else phone end,
                    updated_at = now()
                where id = ?
                """, displayName, phoneProvided, phone, id);
    }

    public int setActive(UUID id, boolean active) {
        return jdbc.update("update vet.app_users set is_active = ?, updated_at = now() where id = ?", active, id);
    }

    public int closeOpenAssignments(UUID userId) {
        return jdbc.update("""
                update vet.user_clinic_roles
                set valid_to = current_date
                where user_id = ? and (valid_to is null or valid_to > current_date)
                """, userId);
    }

    public int closeAssignment(UUID userId, UUID assignmentId) {
        return jdbc.update("""
                update vet.user_clinic_roles
                set valid_to = current_date
                where id = ? and user_id = ? and (valid_to is null or valid_to > current_date)
                """, assignmentId, userId);
    }

    public boolean assignmentExists(UUID userId, UUID assignmentId) {
        return exists("select 1 from vet.user_clinic_roles where id = ? and user_id = ?", assignmentId, userId);
    }

    public int countActiveAssignments(UUID userId) {
        Integer total = jdbc.queryForObject("""
                select count(*) from vet.user_clinic_roles ucr
                where ucr.user_id = ? and %s
                """.formatted(ACTIVE_ROLE_PREDICATE), Integer.class, userId);
        return total == null ? 0 : total;
    }

    /** Hedef kullanıcının yönetici olduğu klinik gruplarından, onsuz aktif yöneticisi kalmayacak olanların sayısı. */
    public int countGroupsLeftWithoutManager(UUID userId) {
        Integer total = jdbc.queryForObject("""
                select count(distinct c.clinic_group_id)
                from vet.user_clinic_roles ucr
                join vet.clinics c on c.organization_id = ucr.organization_id and c.id = ucr.clinic_id
                join vet.role_permissions rp on rp.organization_id = ucr.organization_id and rp.role_id = ucr.role_id
                join vet.permissions p on p.id = rp.permission_id and p.code = 'users.manage'
                where ucr.user_id = ? and %s
                  and not exists (
                      select 1
                      from vet.user_clinic_roles o
                      join vet.app_users ou on ou.organization_id = o.organization_id and ou.id = o.user_id
                      join vet.clinics oc on oc.organization_id = o.organization_id and oc.id = o.clinic_id
                      join vet.role_permissions orp on orp.organization_id = o.organization_id and orp.role_id = o.role_id
                      join vet.permissions op on op.id = orp.permission_id and op.code = 'users.manage'
                      where o.user_id <> ucr.user_id
                        and ou.is_active
                        and o.valid_from <= current_date
                        and (o.valid_to is null or o.valid_to >= current_date)
                        and oc.clinic_group_id = c.clinic_group_id
                  )
                """.formatted(ACTIVE_ROLE_PREDICATE), Integer.class, userId);
        return total == null ? 0 : total;
    }

    public List<RoleResponse> findRoles() {
        Map<UUID, RoleResponse> roles = new LinkedHashMap<>();
        Map<UUID, List<String>> permissions = new LinkedHashMap<>();
        jdbc.query("""
                select r.id, r.code, r.name, r.description, r.is_system_role, p.code as permission_code
                from vet.roles r
                left join vet.role_permissions rp on rp.organization_id = r.organization_id and rp.role_id = r.id
                left join vet.permissions p on p.id = rp.permission_id
                order by r.name, r.id, p.code
                """, rs -> {
            UUID id = rs.getObject("id", UUID.class);
            List<String> codes = permissions.computeIfAbsent(id, k -> new ArrayList<>());
            String permission = rs.getString("permission_code");
            if (permission != null) {
                codes.add(permission);
            }
            roles.computeIfAbsent(id, k -> {
                try {
                    return new RoleResponse(id, rs.getString("code"), rs.getString("name"),
                            rs.getString("description"), rs.getBoolean("is_system_role"), codes);
                } catch (java.sql.SQLException ex) {
                    throw new IllegalStateException(ex);
                }
            });
        });
        return new ArrayList<>(roles.values());
    }

    private String userSelect() {
        return """
                select u.id, u.email, u.display_name, u.phone, u.is_active, u.must_change_password, u.last_login_at, u.created_at
                from vet.app_users u
                """;
    }

    private String searchWhere(String query, Boolean active, List<Object> args) {
        StringBuilder where = new StringBuilder(" where true");
        if (query != null && !query.isBlank()) {
            String like = SqlLike.contains(query);
            where.append(" and (lower(u.display_name) like ? or lower(u.email) like ?)");
            args.add(like);
            args.add(like);
        }
        if (active != null) {
            where.append(" and u.is_active = ?");
            args.add(active);
        }
        return where.toString();
    }

    private boolean exists(String sql, Object... args) {
        try {
            return !jdbc.queryForList(sql, args).isEmpty();
        } catch (EmptyResultDataAccessException ex) {
            return false;
        }
    }
}
