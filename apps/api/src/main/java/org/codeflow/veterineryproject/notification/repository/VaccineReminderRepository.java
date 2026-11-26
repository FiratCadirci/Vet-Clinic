package org.codeflow.veterineryproject.notification.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Aşı hatırlatma görevi: aday aşılar (SECURITY DEFINER) ve kayıt + tür başına tekil "sahiplenme" satırı. */
@Repository
public class VaccineReminderRepository {

    public record ReminderCandidate(UUID vaccineRecordId, UUID animalId, String vaccineName, LocalDate dueDate,
                                    String kind) {
        public boolean overdue() {
            return "OVERDUE".equals(kind);
        }
    }

    private final JdbcTemplate jdbc;

    public VaccineReminderRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<UUID> findActiveOrganizations() {
        return jdbc.queryForList("select * from vet.notification_active_organizations()", UUID.class);
    }

    public List<ReminderCandidate> findCandidates(int daysAhead) {
        return jdbc.query("select * from vet.vaccine_reminder_candidates(?)", (rs, i) -> new ReminderCandidate(
                rs.getObject("vaccine_record_id", UUID.class), rs.getObject("animal_id", UUID.class),
                rs.getString("vaccine_name"), rs.getObject("next_due_at", LocalDate.class), rs.getString("reminder_kind")),
                daysAhead);
    }

    /**
     * Hatırlatmayı sahiplenir. Birincil anahtar sayesinde eşzamanlı iki koşudan yalnız biri true alır;
     * diğeri ilk işlem commit edilene kadar bekler ve satır zaten olduğu için false döner.
     */
    public boolean claim(UUID organizationId, UUID vaccineRecordId, String kind, LocalDate dueDate) {
        return jdbc.update("""
                insert into vet.vaccine_reminder_dispatches (organization_id, vaccine_record_id, reminder_kind, due_date)
                values (?, ?, ?, ?)
                on conflict do nothing
                """, organizationId, vaccineRecordId, kind, Date.valueOf(dueDate)) == 1;
    }
}
