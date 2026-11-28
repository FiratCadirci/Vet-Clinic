package org.codeflow.veterineryproject.report.repository;

import org.codeflow.veterineryproject.report.dto.DashboardDto.DueVaccination;
import org.codeflow.veterineryproject.report.dto.DashboardDto.OpenEncounter;
import org.codeflow.veterineryproject.report.dto.DashboardDto.TodayAppointment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.codeflow.veterineryproject.report.repository.ReportJdbcRepository.LATEST_DOSE_FROM;
import static org.codeflow.veterineryproject.report.repository.ReportJdbcRepository.LATEST_DOSE_WHERE;
import static org.codeflow.veterineryproject.report.repository.ReportJdbcRepository.uuidArray;

/** Genel bakış listeleri: seçili şubenin bugünkü randevuları, açık muayeneleri ve yaklaşan aşıları (temel tablolar). */
@Repository
public class DashboardJdbcRepository {

    private final JdbcTemplate jdbc;

    public DashboardJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<TodayAppointment> appointments(UUID clinicId, OffsetDateTime from, OffsetDateTime to, int limit) {
        return jdbc.query("""
                select ap.id, ap.starts_at, ap.ends_at, ap.animal_id, an.name as animal_name, ap.client_id,
                       c.display_name as client_name, ap.appointment_type, ap.veterinarian_id,
                       sm.full_name as vet_name, ap.status
                from vet.appointments ap
                join vet.animals an on an.organization_id = ap.organization_id and an.id = ap.animal_id
                join vet.clients c on c.organization_id = ap.organization_id and c.id = ap.client_id
                left join vet.veterinarians v on v.organization_id = ap.organization_id and v.id = ap.veterinarian_id
                left join vet.staff_members sm on sm.organization_id = v.organization_id and sm.id = v.staff_member_id
                where ap.clinic_id = ? and ap.status <> 'CANCELLED' and ap.starts_at >= ? and ap.starts_at < ?
                order by ap.starts_at, an.name
                limit ?
                """, (rs, n) -> new TodayAppointment(rs.getObject("id", UUID.class),
                rs.getObject("starts_at", OffsetDateTime.class), rs.getObject("ends_at", OffsetDateTime.class),
                rs.getObject("animal_id", UUID.class), rs.getString("animal_name"),
                rs.getObject("client_id", UUID.class), rs.getString("client_name"), rs.getString("appointment_type"),
                rs.getObject("veterinarian_id", UUID.class), rs.getString("vet_name"), rs.getString("status")),
                clinicId, from, to, limit);
    }

    public List<OpenEncounter> openEncounters(UUID clinicId, int limit) {
        return jdbc.query("""
                select e.id, e.animal_id, an.name as animal_name, e.attending_vet_id, sm.full_name as vet_name,
                       e.encounter_type, e.status, e.started_at
                from vet.encounters e
                join vet.animals an on an.organization_id = e.organization_id and an.id = e.animal_id
                left join vet.veterinarians v on v.organization_id = e.organization_id and v.id = e.attending_vet_id
                left join vet.staff_members sm on sm.organization_id = v.organization_id and sm.id = v.staff_member_id
                where e.clinic_id = ? and e.status <> 'CLOSED'
                order by e.started_at desc
                limit ?
                """, (rs, n) -> new OpenEncounter(rs.getObject("id", UUID.class), rs.getObject("animal_id", UUID.class),
                rs.getString("animal_name"), rs.getObject("attending_vet_id", UUID.class), rs.getString("vet_name"),
                rs.getString("encounter_type"), rs.getString("status"),
                rs.getObject("started_at", OffsetDateTime.class)), clinicId, limit);
    }

    /** Sonraki doz tarihi {@code before} gününden önce olan (gecikmişler dahil) son aşı kayıtları ve hasta sahibi. */
    public List<DueVaccination> dueVaccinations(UUID clinicId, LocalDate today, LocalDate before, int limit) {
        return jdbc.query("""
                select vr.id, vr.animal_id, a.name as animal_name, own.client_id, own.client_name, own.phone,
                       vr.vaccine_name, vr.next_due_at, (vr.next_due_at < ?) as overdue
                """ + LATEST_DOSE_FROM + """
                left join lateral (
                    select c.id as client_id, c.display_name as client_name,
                           (select cc.contact_value from vet.client_contacts cc
                            where cc.organization_id = c.organization_id and cc.client_id = c.id
                              and cc.contact_type = 'PHONE'
                            order by cc.is_primary desc, cc.created_at limit 1) as phone
                    from vet.animal_owners ao
                    join vet.clients c on c.organization_id = ao.organization_id and c.id = ao.client_id
                    where ao.organization_id = vr.organization_id and ao.animal_id = vr.animal_id and ao.valid_to is null
                    order by ao.is_primary_contact desc, ao.is_legal_owner desc, ao.created_at
                    limit 1
                ) own on true
                """ + LATEST_DOSE_WHERE + " order by vr.next_due_at, a.name limit ?",
                (rs, n) -> new DueVaccination(rs.getObject("id", UUID.class), rs.getObject("animal_id", UUID.class),
                        rs.getString("animal_name"), rs.getObject("client_id", UUID.class), rs.getString("client_name"),
                        rs.getString("phone"), rs.getString("vaccine_name"), rs.getObject("next_due_at", LocalDate.class),
                        rs.getBoolean("overdue")),
                today, uuidArray(List.of(clinicId)), before, limit);
    }
}
