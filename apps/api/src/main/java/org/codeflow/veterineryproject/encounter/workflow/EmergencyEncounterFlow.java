package org.codeflow.veterineryproject.encounter.workflow;

import org.codeflow.veterineryproject.encounter.dto.EncounterResponse;
import org.codeflow.veterineryproject.encounter.dto.EncounterStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Acil muayene: kabulde şikâyet ve vücut ısısı, planda uygulanan işlem zorunlu; kritik ısıda uyarı verir. */
@Component
public class EmergencyEncounterFlow extends EncounterFlow {

    static final BigDecimal CRITICAL_LOW_C = new BigDecimal("37.5");
    static final BigDecimal CRITICAL_HIGH_C = new BigDecimal("40");

    @Override
    public String code() {
        return "EMERGENCY";
    }

    @Override
    public String label() {
        return "Acil muayene";
    }

    @Override
    public Set<String> encounterTypes() {
        return Set.of("EMERGENCY");
    }

    @Override
    protected Set<FlowField> requiredFor(EncounterStatus step) {
        return switch (step) {
            case ADMISSION -> EnumSet.of(FlowField.CHIEF_COMPLAINT, FlowField.TEMPERATURE);
            case EXAMINATION -> EnumSet.of(FlowField.EXAMINATION_SUMMARY);
            case DIAGNOSIS -> EnumSet.of(FlowField.ASSESSMENT);
            case PLAN -> EnumSet.of(FlowField.TREATMENT_PLAN, FlowField.PROCEDURES);
            case CLOSED -> EnumSet.noneOf(FlowField.class);
        };
    }

    @Override
    protected List<String> onStepCompleted(EncounterStatus step, EncounterResponse encounter) {
        BigDecimal t = encounter.temperatureC();
        if (step == EncounterStatus.ADMISSION && t != null
                && (t.compareTo(CRITICAL_LOW_C) < 0 || t.compareTo(CRITICAL_HIGH_C) > 0)) {
            return List.of("Kritik vücut ısısı: %s °C".formatted(t.stripTrailingZeros().toPlainString()));
        }
        return List.of();
    }
}
