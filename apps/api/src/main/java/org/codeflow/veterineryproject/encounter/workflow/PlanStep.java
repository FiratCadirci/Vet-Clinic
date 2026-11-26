package org.codeflow.veterineryproject.encounter.workflow;

import org.codeflow.veterineryproject.common.api.BusinessRuleException;
import org.codeflow.veterineryproject.encounter.dto.EncounterResponse;
import org.codeflow.veterineryproject.encounter.dto.EncounterStatus;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

/** Plan: tedavi planı ve (opsiyonel) kontrol tarihi. */
@Component
public class PlanStep extends EncounterStep<PlanStep.Input> {

    public record Input(String treatmentPlan, OffsetDateTime followUpAt) {
    }

    @Override
    protected EncounterStatus requiredStatus() {
        return EncounterStatus.DIAGNOSIS;
    }

    @Override
    protected EncounterStatus targetStatus() {
        return EncounterStatus.PLAN;
    }

    @Override
    protected String stepName() {
        return "plan";
    }

    @Override
    protected void validate(EncounterResponse current, Input input) {
        require(input.treatmentPlan(), "Tedavi planı zorunludur.");
        if (input.followUpAt() != null && input.followUpAt().isBefore(current.startedAt())) {
            throw new BusinessRuleException("Kontrol tarihi muayene başlangıcından önce olamaz.");
        }
    }

    @Override
    protected EncounterResponse apply(EncounterResponse current, Input input) {
        return current.withContent(current.chiefComplaint(), current.history(), current.examinationSummary(),
                current.assessment(), trim(input.treatmentPlan()), current.vetNotes(), input.followUpAt());
    }
}
