package org.codeflow.veterineryproject.common.event;

import org.codeflow.veterineryproject.common.event.DomainEvents.AppointmentStatusChangedEvent;
import org.codeflow.veterineryproject.common.event.DomainEvents.EncounterClosedEvent;
import org.codeflow.veterineryproject.common.event.DomainEvents.VaccineRecordedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Bildirim servisi gelene kadar yalnızca log yazan dinleyici; event'ler commit sonrası işlenir. */
@Component
public class DomainEventLogListener {

    private static final Logger log = LoggerFactory.getLogger(DomainEventLogListener.class);

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEncounterClosed(EncounterClosedEvent event) {
        log.info("EncounterClosed encounterId={} animalId={} appointmentId={}", event.encounterId(), event.animalId(), event.appointmentId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVaccineRecorded(VaccineRecordedEvent event) {
        log.info("VaccineRecorded recordId={} animalId={} nextDueAt={}", event.vaccineRecordId(), event.animalId(), event.nextDueAt());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAppointmentStatusChanged(AppointmentStatusChangedEvent event) {
        log.info("AppointmentStatusChanged appointmentId={} {} -> {}", event.appointmentId(), event.oldStatus(), event.newStatus());
    }
}
