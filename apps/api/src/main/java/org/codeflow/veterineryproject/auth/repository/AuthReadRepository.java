package org.codeflow.veterineryproject.auth.repository;

import org.codeflow.veterineryproject.auth.dto.AuthClinicSummary;
import org.codeflow.veterineryproject.auth.dto.AuthLoginCandidate;
import org.codeflow.veterineryproject.auth.dto.AuthRoleSummary;
import org.codeflow.veterineryproject.auth.dto.OrganizationOption;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class AuthReadRepository {

    private final JdbcTemplate jdbcTemplate;

    public AuthReadRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public UUID resolveOrganizationIdByCode(int organizationCode) {
        return jdbcTemplate.queryForObject(
                "select vet.resolve_organization_by_code(?)",
                UUID.class,
                organizationCode
        );
    }

    public UUID resolveLoginUserId(UUID organizationId, String email) {
        return jdbcTemplate.queryForObject(
                "select vet.resolve_login_user_id(?, ?)",
                UUID.class,
                organizationId,
                email
        );
    }

    public List<OrganizationOption> listActiveOrganizations() {
        return jdbcTemplate.query(
                "select * from vet.list_active_organizations()",
                (rs, rowNum) -> new OrganizationOption(
                        rs.getInt("organization_code"),
                        rs.getString("organization_name")
                )
        );
    }

    public List<AuthLoginCandidate> resolveLoginCandidates(String email, Integer organizationCode) {
        return jdbcTemplate.query(
                "select * from vet.resolve_login_candidates(?, ?)",
                (rs, rowNum) -> new AuthLoginCandidate(
                        rs.getObject("organization_id", UUID.class),
                        rs.getInt("organization_code"),
                        rs.getString("organization_name"),
                        rs.getObject("user_id", UUID.class),
                        rs.getBoolean("is_client")
                ),
                email,
                organizationCode
        );
    }

    public List<UUID> findActiveClinicIds(UUID organizationId, UUID userId) {
        return jdbcTemplate.queryForList(
                """
                select distinct ucr.clinic_id
                from vet.user_clinic_roles ucr
                where ucr.organization_id = ?
                  and ucr.user_id = ?
                  and ucr.valid_from <= current_date
                  and (ucr.valid_to is null or ucr.valid_to >= current_date)
                order by ucr.clinic_id
                """,
                UUID.class,
                organizationId,
                userId
        );
    }

    public List<AuthRoleSummary> findActiveRoles(UUID organizationId, UUID userId) {
        return jdbcTemplate.query(
                """
                select distinct r.id, r.code, r.name
                from vet.user_clinic_roles ucr
                join vet.roles r
                  on r.organization_id = ucr.organization_id
                 and r.id = ucr.role_id
                where ucr.organization_id = ?
                  and ucr.user_id = ?
                  and ucr.valid_from <= current_date
                  and (ucr.valid_to is null or ucr.valid_to >= current_date)
                order by r.code
                """,
                (rs, rowNum) -> new AuthRoleSummary(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("name")),
                organizationId,
                userId
        );
    }

    public List<AuthClinicSummary> findActiveClinics(UUID organizationId, UUID userId) {
        return jdbcTemplate.query(
                """
                select distinct c.id, c.code, c.name
                from vet.user_clinic_roles ucr
                join vet.clinics c
                  on c.organization_id = ucr.organization_id
                 and c.id = ucr.clinic_id
                where ucr.organization_id = ?
                  and ucr.user_id = ?
                  and ucr.valid_from <= current_date
                  and (ucr.valid_to is null or ucr.valid_to >= current_date)
                order by c.name, c.id
                """,
                (rs, rowNum) -> new AuthClinicSummary(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("name")),
                organizationId,
                userId
        );
    }

    public List<String> findActivePermissionCodes(UUID organizationId, UUID userId) {
        return jdbcTemplate.queryForList(
                """
                select distinct p.code
                from vet.user_clinic_roles ucr
                join vet.role_permissions rp
                  on rp.organization_id = ucr.organization_id
                 and rp.role_id = ucr.role_id
                join vet.permissions p
                  on p.id = rp.permission_id
                where ucr.organization_id = ?
                  and ucr.user_id = ?
                  and ucr.valid_from <= current_date
                  and (ucr.valid_to is null or ucr.valid_to >= current_date)
                order by p.code
                """,
                String.class,
                organizationId,
                userId
        );
    }
}

