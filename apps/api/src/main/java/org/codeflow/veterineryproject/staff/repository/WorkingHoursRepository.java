package org.codeflow.veterineryproject.staff.repository;

import org.codeflow.veterineryproject.staff.dto.WorkingHoursDto.VeterinarianOption;
import org.codeflow.veterineryproject.staff.dto.WorkingHoursDto.WorkingHourResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Time;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Hekim çalışma saatleri. Şube satırları RLS ile çağıranın klinik grubuyla sınırlıdır. */
@Repository
public class WorkingHoursRepository {

    private static final String ACTIVE_ROLE = "ucr.valid_from <= current_date and (ucr.valid_to is null or ucr.valid_to >= current_date)";

    private final JdbcTemplate jdbc;

    public WorkingHoursRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<WorkingHourResponse> list(UUID clinicId, UUID veterinarianId) {
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" where wh.is_active");
        if (clinicId != null) {
            where.append(" and wh.clinic_id = ?");
            args.add(clinicId);
        }
        if (veterinarianId != null) {
            where.append(" and wh.veterinarian_id = ?");
            args.add(veterinarianId);
        }
        return jdbc.query("""
                select wh.id, wh.clinic_id, c.name as clinic_name, wh.veterinarian_id, s.user_id,
                       coalesce(u.display_name, s.full_name) as vet_name, wh.day_of_week, wh.starts_at, wh.ends_at
                from vet.veterinarian_working_hours wh
                join vet.clinics c on c.organization_id = wh.organization_id and c.id = wh.clinic_id
                join vet.veterinarians v on v.organization_id = wh.organization_id and v.id = wh.veterinarian_id
                join vet.staff_members s on s.organization_id = v.organization_id and s.id = v.staff_member_id
                left join vet.app_users u on u.organization_id = s.organization_id and u.id = s.user_id
                """ + where + " order by vet_name, wh.veterinarian_id, c.name, wh.day_of_week, wh.starts_at",
                (rs, i) -> new WorkingHourResponse(
                        rs.getObject("id", UUID.class),
                        rs.getObject("clinic_id", UUID.class),
                        rs.getString("clinic_name"),
                        rs.getObject("veterinarian_id", UUID.class),
                        rs.getObject("user_id", UUID.class),
                        rs.getString("vet_name"),
                        rs.getInt("day_of_week"),
                        hhmm(rs.getObject("starts_at", LocalTime.class)),
                        hhmm(rs.getObject("ends_at", LocalTime.class))
                ), args.toArray());
    }

    /** Şubede geçerli rol ataması olan, aktif hekim profilli personel. */
    public List<VeterinarianOption> veterinariansAt(UUID clinicId) {
        return jdbc.query("""
                select distinct v.id, s.user_id, coalesce(u.display_name, s.full_name) as name, v.license_no
                from vet.veterinarians v
                join vet.staff_members s on s.organization_id = v.organization_id and s.id = v.staff_member_id
                join vet.app_users u on u.organization_id = s.organization_id and u.id = s.user_id
                join vet.user_clinic_roles ucr on ucr.organization_id = u.organization_id and ucr.user_id = u.id
                where ucr.clinic_id = ? and v.is_active and s.is_active and u.is_active and %s
                order by name, v.id
                """.formatted(ACTIVE_ROLE), (rs, i) -> new VeterinarianOption(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("name"),
                rs.getString("license_no")
        ), clinicId);
    }

    public boolean veterinarianExists(UUID veterinarianId) {
        return !jdbc.queryForList("select 1 from vet.veterinarians where id = ?", Integer.class, veterinarianId).isEmpty();
    }

    public boolean veterinarianAssignedTo(UUID veterinarianId, UUID clinicId) {
        return veterinariansAt(clinicId).stream().anyMatch(v -> v.veterinarianId().equals(veterinarianId));
    }

    public int deleteFor(UUID veterinarianId, UUID clinicId) {
        return jdbc.update("delete from vet.veterinarian_working_hours where veterinarian_id = ? and clinic_id = ?",
                veterinarianId, clinicId);
    }

    public void insert(UUID organizationId, UUID clinicId, UUID veterinarianId, int dayOfWeek, LocalTime start, LocalTime end) {
        jdbc.update("""
                insert into vet.veterinarian_working_hours (organization_id, clinic_id, veterinarian_id, day_of_week, starts_at, ends_at)
                values (?, ?, ?, ?, ?, ?)
                """, organizationId, clinicId, veterinarianId, dayOfWeek, Time.valueOf(start), Time.valueOf(end));
    }

    private static String hhmm(LocalTime time) {
        return time == null ? null : time.toString().substring(0, 5);
    }
}
