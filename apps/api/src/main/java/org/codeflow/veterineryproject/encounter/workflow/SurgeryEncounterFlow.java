package org.codeflow.veterineryproject.encounter.workflow;

import org.codeflow.veterineryproject.encounter.dto.EncounterResponse;
import org.codeflow.veterineryproject.encounter.dto.EncounterStatus;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Cerrahi: kabulde anamnez, incelemede (anestezi dozu için) kilo, planda uygulanan işlem zorunlu; operasyon notu yoksa uyarı. */
@Component
public class SurgeryEncounterFlow extends EncounterFlow {

    @Override
    public String code() {
        return "SURGERY";
    }

    @Override
    public String label() {
        return "Cerrahi operasyon";
    }

    @Override
    public Set<String> encounterTypes() {
        return Set.of("SURGERY");
    }

    @Override
    protected Set<FlowField> requiredFor(EncounterStatus step) {
        return switch (step) {
            case ADMISSION -> EnumSet.of(FlowField.CHIEF_COMPLAINT, FlowField.HISTORY);
            case EXAMINATION -> EnumSet.of(FlowField.EXAMINATION_SUMMARY, FlowField.WEIGHT);
            case DIAGNOSIS -> EnumSet.of(FlowField.ASSESSMENT);
            case PLAN -> EnumSet.of(FlowField.TREATMENT_PLAN, FlowField.PROCEDURES);
            case CLOSED -> EnumSet.noneOf(FlowField.class);
        };
    }

    @Override
    protected List<String> beforeClose(EncounterResponse encounter) {
        return FlowField.VET_NOTES.isFilledIn(encounter) ? List.of() : List.of("Operasyon notu (hekim notu) girilmemiş.");
    }
}
