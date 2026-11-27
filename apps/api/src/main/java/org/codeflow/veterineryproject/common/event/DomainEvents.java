package org.codeflow.veterineryproject.common.event;

import java.time.LocalDate;
import java.util.UUID;

/** Domain event'leri yalnızca kimlik taşır; tıbbi içerik içermez. */
public final class DomainEvents {

    private DomainEvents() {
    }

    public record EncounterClosedEvent(UUID organizationId, UUID encounterId, UUID animalId, UUID appointmentId, UUID clinicId) {
    }

    public record VaccineRecordedEvent(UUID organizationId, UUID vaccineRecordId, UUID animalId, UUID encounterId,
                                       UUID clinicId, LocalDate nextDueAt) {
    }

    /** Personel daveti oluşturuldu; bildirim modülü davet e-postasını gönderir. Token yalnızca e-posta bağlantısı içindir. */
    public record StaffInvitedEvent(UUID organizationId, UUID invitationId, String email, String inviteToken,
                                    String organizationName, UUID invitedByUserId) {
    }

    /** Bir ürünün şubedeki stoğu minimum seviyenin altına indi. */
    public record StockLowEvent(UUID organizationId, UUID clinicId, UUID productId, String productName,
                                java.math.BigDecimal quantity, java.math.BigDecimal minimum) {
    }

    public record AppointmentStatusChangedEvent(UUID organizationId, UUID appointmentId, String oldStatus,
                                                String newStatus, UUID clinicId) {
    }

    /** Randevunun zamanı değişti (erteleme); bildirim modülü hayvan sahibine yeni saati bildirir. */
    public record AppointmentRescheduledEvent(UUID organizationId, UUID appointmentId, UUID clinicId) {
    }

    /** Satış faturası kesildi (DRAFT → ISSUED); bildirim modülü faturanın müşterisine haber verir. */
    public record InvoiceIssuedEvent(UUID organizationId, UUID invoiceId, UUID clientId, UUID clinicId) {
    }
}
