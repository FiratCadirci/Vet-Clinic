package org.codeflow.veterineryproject.vaccine.factory;

import org.springframework.stereotype.Component;

import java.util.List;

/** Köpek: iç-dış parazit ve Corona 6. hafta, Karma (DHPPi+L) 8. haftadan 21 gün arayla 3 doz, Bronşin 2 doz, Kuduz 12. hafta; yıllık rapel. Tıbbi takvim klinik tarafından doğrulanır. */
@Component
public class DogVaccineScheduleFactory extends AbstractSpeciesScheduleFactory {

    @Override
    public boolean supports(String speciesCode) {
        return "DOG".equalsIgnoreCase(speciesCode);
    }

    @Override
    protected int minimumFirstDoseAgeDays() {
        return 42;
    }

    @Override
    protected List<ProtocolDraft> defaultSchedule() {
        return List.of(
                ProtocolDraft.weeks("PARASITE", "İç-dış parazit", 6, 1, 21, 90),
                ProtocolDraft.weeks("CORONA", "Corona (CCoV)", 6, 2, 21, 365),
                ProtocolDraft.weeks("CORE", "Karma (DHPPi+L)", 8, 3, 21, 365),
                ProtocolDraft.weeks("KENNEL", "Bronşin (Kennel Cough)", 10, 2, 21, 365),
                ProtocolDraft.weeks("RABIES", "Kuduz", 12, 1, 21, 365));
    }
}
