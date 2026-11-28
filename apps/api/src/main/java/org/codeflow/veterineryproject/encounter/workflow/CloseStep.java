package org.codeflow.veterineryproject.encounter.workflow;

import org.codeflow.veterineryproject.encounter.dto.EncounterResponse;
import org.codeflow.veterineryproject.encounter.dto.EncounterStatus;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

/** Kapatma: zorunlu alanlar tamamlanmış olmalıdır; kapanış sonrası kayıt değiştirilemez. */
@Component
public class CloseStep extends EncounterStep<String> {

    @Override
    protected EncounterStatus targetStatus() {
        return EncounterStatus.CLOSED;
    }

    @Override
    protected String stepName() {
        return "kapatma";
    }

    @Override
    protected EncounterResponse apply(EncounterResponse current, String vetNotes) {
        String notes = trim(vetNotes) != null ? trim(vetNotes) : current.vetNotes();
        return current.withContent(current.chiefComplaint(), current.history(), current.examinationSummary(),
                current.assessment(), current.treatmentPlan(), notes, current.followUpAt())
                .withEndedAt(OffsetDateTime.now());
    }
}
