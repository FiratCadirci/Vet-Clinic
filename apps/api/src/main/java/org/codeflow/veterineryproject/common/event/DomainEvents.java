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

    public record AppointmentStatusChangedEvent(UUID organizationId, UUID appointmentId, String oldStatus,
                                                String newStatus, UUID clinicId) {
    }
}
