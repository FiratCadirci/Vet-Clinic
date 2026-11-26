package org.codeflow.veterineryproject.encounter.workflow;

import org.codeflow.veterineryproject.encounter.dto.EncounterResponse;
import org.codeflow.veterineryproject.encounter.dto.EncounterStatus;
import org.springframework.stereotype.Component;

/** İnceleme: fizik muayene bulguları kaydedilir. */
@Component
public class ExaminationStep extends EncounterStep<String> {

    @Override
    protected EncounterStatus requiredStatus() {
        return EncounterStatus.ADMISSION;
    }

    @Override
    protected EncounterStatus targetStatus() {
        return EncounterStatus.EXAMINATION;
    }

    @Override
    protected String stepName() {
        return "inceleme";
    }

    @Override
    protected void validate(EncounterResponse current, String examinationSummary) {
        require(examinationSummary, "Fizik muayene bulgusu zorunludur.");
    }

    @Override
    protected EncounterResponse apply(EncounterResponse current, String examinationSummary) {
        return current.withContent(current.chiefComplaint(), current.history(), trim(examinationSummary),
                current.assessment(), current.treatmentPlan(), current.vetNotes(), current.followUpAt());
    }
}
