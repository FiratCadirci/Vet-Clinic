package org.codeflow.veterineryproject.user.repository;

import org.codeflow.veterineryproject.user.dto.VeterinarianResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class VeterinarianRepository {

    private static final RowMapper<VeterinarianResponse> MAPPER = (rs, i) -> new VeterinarianResponse(
            rs.getObject("id", UUID.class),
            rs.getObject("user_id", UUID.class),
            rs.getString("license_no"),
            rs.getString("license_authority"),
            rs.getString("specialty"),
            rs.getObject("graduation_year", Integer.class),
            rs.getBoolean("is_active")
    );

    private static final String SELECT = """
            select v.id, s.user_id, v.license_no, v.license_authority, v.specialty, v.graduation_year,
                   (v.is_active and s.is_active) as is_active
            from vet.veterinarians v
            join vet.staff_members s on s.organization_id = v.organization_id and s.id = v.staff_member_id
            """;

    private final JdbcTemplate jdbc;

    public VeterinarianRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<VeterinarianResponse> findByUser(UUID userId) {
        List<VeterinarianResponse> rows = jdbc.query(SELECT + " where s.user_id = ?", MAPPER, userId);
        return rows.stream().findFirst();
    }

    public Optional<UUID> findActiveIdByUser(UUID userId) {
        return findByUser(userId).filter(VeterinarianResponse::active).map(VeterinarianResponse::id);
    }

    public UUID findOrCreateStaffMember(UUID organizationId, UUID userId, String fullName, String email, String phone) {
        List<UUID> existing = jdbc.queryForList("select id from vet.staff_members where user_id = ?", UUID.class, userId);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into vet.staff_members (id, organization_id, user_id, full_name, job_title, phone, email, is_active)
                values (?, ?, ?, ?, 'Veteriner Hekim', ?, ?, true)
                """, id, organizationId, userId, fullName, phone, email);
        return id;
    }

    public UUID insert(UUID organizationId, UUID staffMemberId, String licenseNo, String authority, String specialty, Integer year) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into vet.veterinarians (id, organization_id, staff_member_id, license_no, license_authority, specialty, graduation_year, is_active)
                values (?, ?, ?, ?, ?, ?, ?, true)
                """, id, organizationId, staffMemberId, licenseNo, authority, specialty, year);
        return id;
    }
}
