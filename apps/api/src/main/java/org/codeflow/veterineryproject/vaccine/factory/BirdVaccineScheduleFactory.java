package org.codeflow.veterineryproject.vaccine.factory;

import org.springframework.stereotype.Component;

import java.util.List;

/** Kuş: rutin aşı yerine iç-dış parazit kontrolü (6 ayda bir); kalemler klinik tarafından eklenebilir. Tıbbi takvim klinik tarafından doğrulanır. */
@Component
public class BirdVaccineScheduleFactory extends AbstractSpeciesScheduleFactory {

    @Override
    public boolean supports(String speciesCode) {
        return "BIRD".equalsIgnoreCase(speciesCode);
    }

    @Override
    protected int minimumFirstDoseAgeDays() {
        return 56;
    }

    @Override
    protected List<ProtocolDraft> defaultSchedule() {
        return List.of(
                ProtocolDraft.weeks("PARASITE", "İç-dış parazit", 8, 1, 21, 180));
    }
}
