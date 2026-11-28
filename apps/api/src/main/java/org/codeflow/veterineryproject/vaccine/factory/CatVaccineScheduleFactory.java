package org.codeflow.veterineryproject.vaccine.factory;

import org.springframework.stereotype.Component;

import java.util.List;

/** Kedi: iç-dış parazit 6. hafta, Karma (FVRCP) 8. hafta 2 doz, Lösemi (FeLV) 10. hafta 2 doz, Kuduz 12. hafta; yıllık rapel. Tıbbi takvim klinik tarafından doğrulanır. */
@Component
public class CatVaccineScheduleFactory extends AbstractSpeciesScheduleFactory {

    @Override
    public boolean supports(String speciesCode) {
        return "CAT".equalsIgnoreCase(speciesCode);
    }

    @Override
    protected int minimumFirstDoseAgeDays() {
        return 56;
    }

    @Override
    protected List<ProtocolDraft> defaultSchedule() {
        return List.of(
                ProtocolDraft.weeks("PARASITE", "İç-dış parazit", 6, 1, 21, 90),
                ProtocolDraft.weeks("CORE", "Karma (FVRCP)", 8, 2, 21, 365),
                ProtocolDraft.weeks("FELV", "Lösemi (FeLV)", 10, 2, 21, 365),
                ProtocolDraft.weeks("RABIES", "Kuduz", 12, 1, 21, 365));
    }
}
