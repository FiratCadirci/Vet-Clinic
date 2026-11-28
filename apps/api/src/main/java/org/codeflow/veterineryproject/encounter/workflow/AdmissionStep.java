package org.codeflow.veterineryproject.encounter.workflow;

import org.codeflow.veterineryproject.encounter.dto.EncounterResponse;
import org.codeflow.veterineryproject.encounter.dto.EncounterStatus;
import org.springframework.stereotype.Component;

/** Kabul: muayene kaydı açılır; zorunlu kabul alanları muayene türünün akışına göre denetlenir. */
@Component
public class AdmissionStep extends EncounterStep<AdmissionStep.Input> {

    public record Input(String chiefComplaint, String history) {
    }

    @Override
    protected EncounterStatus targetStatus() {
        return EncounterStatus.ADMISSION;
    }

    @Override
    protected String stepName() {
        return "kabul";
    }

    @Override
    protected EncounterResponse apply(EncounterResponse current, Input input) {
        return current.withContent(trim(input.chiefComplaint()), trim(input.history()), current.examinationSummary(),
                current.assessment(), current.treatmentPlan(), current.vetNotes(), current.followUpAt());
    }
}
