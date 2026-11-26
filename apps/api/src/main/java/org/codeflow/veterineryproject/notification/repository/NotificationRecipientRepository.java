package org.codeflow.veterineryproject.notification.repository;

import org.codeflow.veterineryproject.notification.model.NotificationRecipient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Bildirim alıcılarını ve mesaj için gereken asgari bilgiyi çözer. Sahip ve personel listeleri V25'teki
 * SECURITY DEFINER fonksiyonlarıyla gelir; fonksiyonlar yalnız app.organization_id'deki organizasyonu okur.
 */
@Repository
public class NotificationRecipientRepository {

    /** Hayvanın güncel sahibi: mobil hesabı (varsa) ve e-postası. */
    public record OwnerContact(UUID clientId, String clientName, UUID userId, String email, String animalName) {
        public NotificationRecipient toRecipient() {
            return new NotificationRecipient(userId, clientId, email, clientName);
        }
    }

    public record AppointmentSummary(UUID animalId, UUID clientId, OffsetDateTime startsAt, String clinicName) {
    }

    private final JdbcTemplate jdbc;

    public NotificationRecipientRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<OwnerContact> findAnimalOwners(UUID animalId) {
        return jdbc.query("select * from vet.notification_animal_owners(?)", (rs, i) -> new OwnerContact(
                rs.getObject("client_id", UUID.class), rs.getString("client_name"), rs.getObject("owner_user_id", UUID.class),
                rs.getString("owner_email"), rs.getString("animal_name")), animalId);
    }

    public List<NotificationRecipient> findClinicStaff(UUID clinicId, String permission) {
        return jdbc.query("select * from vet.notification_clinic_staff(?, ?)", (rs, i) -> NotificationRecipient.staff(
                rs.getObject("user_id", UUID.class), rs.getString("display_name"), rs.getString("email")), clinicId, permission);
    }

    public Optional<AppointmentSummary> findAppointment(UUID appointmentId) {
        return jdbc.query("select * from vet.notification_appointment(?)", (rs, i) -> new AppointmentSummary(
                rs.getObject("animal_id", UUID.class), rs.getObject("client_id", UUID.class),
                rs.getObject("starts_at", OffsetDateTime.class), rs.getString("clinic_name")), appointmentId)
                .stream().findFirst();
    }

    /** Aşıyı kaydeden personelin bağlamında okunur (RLS); görünmüyorsa boş. */
    public Optional<String> findVaccineName(UUID vaccineRecordId) {
        return jdbc.queryForList("select vaccine_name from vet.vaccine_records where id = ?", String.class, vaccineRecordId)
                .stream().findFirst();
    }

    /** Oturumdaki kullanıcı bir hayvan sahibinin mobil hesabı mı (RLS sahibin kendi müşteri kaydını gösterir). */
    public boolean isClientAccount(UUID userId) {
        Boolean exists = jdbc.queryForObject("select exists (select 1 from vet.clients where app_user_id = ?)",
                Boolean.class, userId);
        return Boolean.TRUE.equals(exists);
    }
}
