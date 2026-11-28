package org.codeflow.veterineryproject.encounter.workflow;

import org.codeflow.veterineryproject.encounter.dto.EncounterResponse;
import org.codeflow.veterineryproject.encounter.dto.EncounterStatus;
import org.springframework.stereotype.Component;

/** Tanı: değerlendirme/tanı metni kaydedilir. */
@Component
public class DiagnosisStep extends EncounterStep<String> {

    @Override
    protected EncounterStatus targetStatus() {
        return EncounterStatus.DIAGNOSIS;
    }

    @Override
    protected String stepName() {
        return "tanı";
    }

    @Override
    protected EncounterResponse apply(EncounterResponse current, String assessment) {
        return current.withContent(current.chiefComplaint(), current.history(), current.examinationSummary(),
                trim(assessment), current.treatmentPlan(), current.vetNotes(), current.followUpAt());
    }
}
