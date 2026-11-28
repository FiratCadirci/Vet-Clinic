package org.codeflow.veterineryproject.encounter.workflow;

import org.codeflow.veterineryproject.encounter.dto.EncounterStatus;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

/** Genel muayene: tüm adımlar; her adımın temel alanı zorunlu. Kontrol, tele-tıp ve diğer ziyaretler de bu akışı kullanır. */
@Component
public class GeneralEncounterFlow extends EncounterFlow {

    @Override
    public String code() {
        return "GENERAL";
    }

    @Override
    public String label() {
        return "Genel muayene";
    }

    @Override
    public Set<String> encounterTypes() {
        return Set.of("CONSULTATION", "FOLLOW_UP", "TELEMEDICINE", "OTHER");
    }

    @Override
    protected Set<FlowField> requiredFor(EncounterStatus step) {
        return switch (step) {
            case ADMISSION -> EnumSet.of(FlowField.CHIEF_COMPLAINT);
            case EXAMINATION -> EnumSet.of(FlowField.EXAMINATION_SUMMARY);
            case DIAGNOSIS -> EnumSet.of(FlowField.ASSESSMENT);
            case PLAN -> EnumSet.of(FlowField.TREATMENT_PLAN);
            case CLOSED -> EnumSet.noneOf(FlowField.class);
        };
    }
}
