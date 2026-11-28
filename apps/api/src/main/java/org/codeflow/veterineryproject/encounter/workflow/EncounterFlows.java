package org.codeflow.veterineryproject.encounter.workflow;

import org.codeflow.veterineryproject.encounter.dto.EncounterFlowResponse;
import org.springframework.stereotype.Component;

import java.util.List;

/** Muayene türüne (encounter_type) göre akışı seçer; tanımsız tür genel akışa düşer. Akışlar durumsuzdur. */
@Component
public class EncounterFlows {

    private final List<EncounterFlow> flows;
    private final GeneralEncounterFlow general;

    public EncounterFlows(GeneralEncounterFlow general, VaccinationVisitFlow vaccination, EmergencyEncounterFlow emergency,
                          SurgeryEncounterFlow surgery) {
        this.general = general;
        this.flows = List.of(general, vaccination, emergency, surgery);
    }

    /** Spring dışı (birim test) kullanım için dört akışla kurulur. */
    public static EncounterFlows standard() {
        return new EncounterFlows(new GeneralEncounterFlow(), new VaccinationVisitFlow(), new EmergencyEncounterFlow(),
                new SurgeryEncounterFlow());
    }

    public EncounterFlow forType(String encounterType) {
        return flows.stream().filter(f -> f.encounterTypes().contains(encounterType)).findFirst().orElse(general);
    }

    public List<EncounterFlowResponse> describeAll() {
        return flows.stream().map(EncounterFlow::describe).toList();
    }
}
