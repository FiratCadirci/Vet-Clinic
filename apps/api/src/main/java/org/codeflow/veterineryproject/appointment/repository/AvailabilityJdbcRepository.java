package org.codeflow.veterineryproject.appointment.repository;

import org.codeflow.veterineryproject.appointment.dto.BookingOptionsResponse.ServiceOption;
import org.codeflow.veterineryproject.appointment.dto.BookingOptionsResponse.VeterinarianOption;
import org.codeflow.veterineryproject.appointment.model.BusyInterval;
import org.codeflow.veterineryproject.appointment.model.WorkingWindow;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Uygun saat hesabının okuma sorguları. Tümü RLS altında çalışır; hekimin dolu aralıkları SECURITY DEFINER fonksiyondan gelir. */
@Repository
public class AvailabilityJdbcRepository {

    public record ServiceRow(UUID id, String name, Integer durationMinutes) {
    }

    private final JdbcTemplate jdbc;

    public AvailabilityJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Şube çağıranın klinik grubunda değilse RLS satırı gizler (boş döner). */
    public Optional<String> clinicTimezone(UUID clinicId) {
        return jdbc.query("select timezone_name from vet.clinics where id = ?",
                (rs, i) -> rs.getString("timezone_name"), clinicId).stream().findFirst();
    }

    public Optional<ServiceRow> activeService(UUID serviceId) {
        return jdbc.query("select id, name, duration_minutes from vet.service_catalog where id = ? and is_active",
                (rs, i) -> new ServiceRow(rs.getObject("id", UUID.class), rs.getString("name"),
                        rs.getObject("duration_minutes", Integer.class)), serviceId).stream().findFirst();
    }

    /** Randevunun süresi (dakika); RLS ile görünmeyen randevu boş döner. */
    public Optional<Integer> appointmentMinutes(UUID appointmentId) {
        return jdbc.query("select (extract(epoch from (ends_at - starts_at)) / 60)::int as minutes from vet.appointments where id = ?",
                (rs, i) -> rs.getInt("minutes"), appointmentId).stream().findFirst();
    }

    public boolean veterinarianActive(UUID veterinarianId) {
        return !jdbc.queryForList("select 1 from vet.veterinarians where id = ? and is_active", veterinarianId).isEmpty();
    }

    /** day_of_week: PostgreSQL DOW (0 = Pazar). Tarihte geçerli, aktif pencereler. */
    public List<WorkingWindow> workingWindows(UUID clinicId, UUID veterinarianId, LocalDate date, int dayOfWeek) {
        return jdbc.query("""
                select starts_at, ends_at from vet.veterinarian_working_hours
                where clinic_id = ? and veterinarian_id = ? and day_of_week = ? and is_active
                  and effective_from <= ? and (effective_to is null or effective_to >= ?)
                order by starts_at
                """, (rs, i) -> new WorkingWindow(rs.getObject("starts_at", LocalTime.class),
                        rs.getObject("ends_at", LocalTime.class)),
                clinicId, veterinarianId, dayOfWeek, Date.valueOf(date), Date.valueOf(date));
    }

    public List<BusyInterval> busyIntervals(UUID veterinarianId, OffsetDateTime from, OffsetDateTime to,
                                            UUID excludeAppointmentId) {
        return jdbc.query("select starts_at, ends_at from vet.veterinarian_busy_intervals(?, ?, ?, ?)",
                (rs, i) -> new BusyInterval(rs.getObject("starts_at", OffsetDateTime.class),
                        rs.getObject("ends_at", OffsetDateTime.class)),
                veterinarianId, from, to, excludeAppointmentId);
    }

    public List<ServiceOption> activeServices() {
        return jdbc.query("""
                select id, code, name, duration_minutes, default_price from vet.service_catalog
                where is_active order by lower(name), id
                """, (rs, i) -> new ServiceOption(rs.getObject("id", UUID.class), rs.getString("code"),
                rs.getString("name"), rs.getObject("duration_minutes", Integer.class),
                rs.getObject("default_price", BigDecimal.class)));
    }

    /** Şubede rolü veya çalışma saati olan aktif hekimler. */
    public List<VeterinarianOption> veterinariansForClinic(UUID clinicId) {
        return jdbc.query("""
                select v.id, sm.full_name
                from vet.veterinarians v
                join vet.staff_members sm on sm.organization_id = v.organization_id and sm.id = v.staff_member_id
                where v.is_active and sm.is_active
                  and (exists (select 1 from vet.veterinarian_working_hours wh
                               where wh.organization_id = v.organization_id and wh.veterinarian_id = v.id
                                 and wh.clinic_id = ? and wh.is_active
                                 and (wh.effective_to is null or wh.effective_to >= current_date))
                       or exists (select 1 from vet.user_clinic_roles ucr
                                  where ucr.organization_id = sm.organization_id and ucr.user_id = sm.user_id
                                    and ucr.clinic_id = ? and ucr.valid_from <= current_date
                                    and (ucr.valid_to is null or ucr.valid_to >= current_date)))
                order by lower(sm.full_name), v.id
                """, (rs, i) -> new VeterinarianOption(rs.getObject("id", UUID.class), rs.getString("full_name")),
                clinicId, clinicId);
    }
}
