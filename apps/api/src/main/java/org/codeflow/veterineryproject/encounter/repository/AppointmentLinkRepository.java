package org.codeflow.veterineryproject.encounter.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Randevu satırlarını muayene akışı için okur/günceller; eski JPA randevu sınıflarına dokunmaz. */
@Repository
public class AppointmentLinkRepository {

    public record AppointmentRow(UUID id, UUID clinicId, UUID animalId, String status, String reason) {
    }

    private final JdbcTemplate jdbc;

    public AppointmentLinkRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<AppointmentRow> findForUpdate(UUID id) {
        List<AppointmentRow> rows = jdbc.query("""
                select id, clinic_id, animal_id, status, reason from vet.appointments where id = ? for update
                """, (rs, i) -> new AppointmentRow(rs.getObject("id", UUID.class), rs.getObject("clinic_id", UUID.class),
                rs.getObject("animal_id", UUID.class), rs.getString("status"), rs.getString("reason")), id);
        return rows.stream().findFirst();
    }

    public boolean exists(UUID id) {
        Integer n = jdbc.queryForObject("select count(*) from vet.appointments where id = ?", Integer.class, id);
        return n != null && n > 0;
    }

    public void changeStatus(UUID organizationId, UUID appointmentId, String oldStatus, String newStatus, UUID userId, String note) {
        jdbc.update("update vet.appointments set status = ?, updated_at = now() where id = ?", newStatus, appointmentId);
        jdbc.update("""
                insert into vet.appointment_status_history (organization_id, appointment_id, old_status, new_status, changed_by, note)
                values (?, ?, ?, ?, ?, ?)
                """, organizationId, appointmentId, oldStatus, newStatus, userId, note);
    }
}
