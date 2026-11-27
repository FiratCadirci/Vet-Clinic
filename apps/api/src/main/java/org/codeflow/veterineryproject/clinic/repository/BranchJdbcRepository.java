package org.codeflow.veterineryproject.clinic.repository;

import org.codeflow.veterineryproject.clinic.dto.BranchDto.BranchRequest;
import org.codeflow.veterineryproject.clinic.dto.BranchDto.BranchResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/** Şube yazma işlemleri. RLS (clinic_group_scope) yalnızca çağıranın klinik grubundaki şubeleri gösterir/yazdırır. */
@Repository
public class BranchJdbcRepository {

    private static final RowMapper<BranchResponse> MAPPER = (rs, i) -> new BranchResponse(
            rs.getObject("id", UUID.class),
            rs.getObject("organization_id", UUID.class),
            rs.getObject("clinic_group_id", UUID.class),
            rs.getString("code"),
            rs.getString("name"),
            rs.getString("phone"),
            rs.getString("email"),
            rs.getString("country_code"),
            rs.getString("city"),
            rs.getString("district"),
            rs.getString("address_line"),
            rs.getString("postal_code"),
            rs.getString("timezone_name"),
            rs.getBoolean("is_active"),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class)
    );

    private final JdbcTemplate jdbc;

    public BranchJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<BranchResponse> find(UUID id) {
        return jdbc.query("select * from vet.clinics where id = ?", MAPPER, id).stream().findFirst();
    }

    public void insert(UUID id, UUID organizationId, UUID clinicGroupId, String code, BranchRequest r) {
        jdbc.update("""
                insert into vet.clinics (id, organization_id, clinic_group_id, code, name, phone, email, city, district,
                                         address_line, postal_code, is_active)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, true)
                """, id, organizationId, clinicGroupId, code, r.name(), r.phone(), r.email(), r.city(), r.district(),
                r.addressLine(), r.postalCode());
    }

    public int update(UUID id, BranchRequest r) {
        return jdbc.update("""
                update vet.clinics
                set name = ?, phone = ?, email = ?, city = ?, district = ?, address_line = ?, postal_code = ?, updated_at = now()
                where id = ?
                """, r.name(), r.phone(), r.email(), r.city(), r.district(), r.addressLine(), r.postalCode(), id);
    }

    public int setActive(UUID id, boolean active) {
        return jdbc.update("update vet.clinics set is_active = ?, updated_at = now() where id = ?", active, id);
    }

    /** Şubeyi açan kullanıcının bulunduğu şubedeki geçerli rolleri yeni şubeye de atanır (yoksa yeni şubeyi göremez). */
    public int copyActiveAssignments(UUID organizationId, UUID userId, UUID fromClinicId, UUID toClinicId) {
        return jdbc.update("""
                insert into vet.user_clinic_roles (organization_id, user_id, clinic_id, role_id, valid_from, assigned_by)
                select distinct ucr.organization_id, ucr.user_id, ?::uuid, ucr.role_id, current_date, ucr.user_id
                from vet.user_clinic_roles ucr
                where ucr.organization_id = ? and ucr.user_id = ? and ucr.clinic_id = ?
                  and ucr.valid_from <= current_date and (ucr.valid_to is null or ucr.valid_to >= current_date)
                """, toClinicId, organizationId, userId, fromClinicId);
    }
}
