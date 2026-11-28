package org.codeflow.veterineryproject.encounter.workflow;

import org.codeflow.veterineryproject.encounter.dto.EncounterResponse;
import org.codeflow.veterineryproject.encounter.dto.EncounterStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Aşı ziyareti: daha hafif akış, tanı adımı yoktur (gerekirse tanı plan aşamasında girilir). Aşı öncesi
 * uygunluk için inceleme adımında kilo ve vücut ısısı zorunludur; ateşli hastada uyarı verilir.
 */
@Component
public class VaccinationVisitFlow extends EncounterFlow {

    static final BigDecimal FEVER_THRESHOLD_C = new BigDecimal("39.5");

    @Override
    public String code() {
        return "VACCINATION";
    }

    @Override
    public String label() {
        return "Aşı ziyareti";
    }

    @Override
    public Set<String> encounterTypes() {
        return Set.of("VACCINATION");
    }

    @Override
    protected List<EncounterStatus> steps() {
        return List.of(EncounterStatus.ADMISSION, EncounterStatus.EXAMINATION, EncounterStatus.PLAN);
    }

    @Override
    protected Set<FlowField> fieldsFor(EncounterStatus step) {
        if (step == EncounterStatus.PLAN) {
            return EnumSet.of(FlowField.ASSESSMENT, FlowField.TREATMENT_PLAN, FlowField.PROCEDURES);
        }
        return super.fieldsFor(step);
    }

    @Override
    protected Set<FlowField> requiredFor(EncounterStatus step) {
        return switch (step) {
            case EXAMINATION -> EnumSet.of(FlowField.WEIGHT, FlowField.TEMPERATURE);
            case PLAN -> EnumSet.of(FlowField.TREATMENT_PLAN);
            default -> EnumSet.noneOf(FlowField.class);
        };
    }

    @Override
    protected List<String> onStepCompleted(EncounterStatus step, EncounterResponse encounter) {
        if (step == EncounterStatus.EXAMINATION && encounter.temperatureC() != null
                && encounter.temperatureC().compareTo(FEVER_THRESHOLD_C) >= 0) {
            return List.of("Ateşli hastada aşılama ertelenmelidir.");
        }
        return List.of();
    }
}
