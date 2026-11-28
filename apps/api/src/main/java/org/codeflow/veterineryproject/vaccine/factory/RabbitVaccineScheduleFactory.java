package org.codeflow.veterineryproject.vaccine.factory;

import org.springframework.stereotype.Component;

import java.util.List;

/** Tavşan: iç-dış parazit ve Miksomatozis 6. hafta (6 ayda bir), RHD (Viral Hemorajik Hastalık) 10. hafta (yıllık). Tıbbi takvim klinik tarafından doğrulanır. */
@Component
public class RabbitVaccineScheduleFactory extends AbstractSpeciesScheduleFactory {

    @Override
    public boolean supports(String speciesCode) {
        return "RABBIT".equalsIgnoreCase(speciesCode);
    }

    @Override
    protected int minimumFirstDoseAgeDays() {
        return 42;
    }

    @Override
    protected List<ProtocolDraft> defaultSchedule() {
        return List.of(
                ProtocolDraft.weeks("PARASITE", "İç-dış parazit", 6, 1, 21, 90),
                ProtocolDraft.weeks("MYXO", "Miksomatozis", 6, 1, 21, 180),
                ProtocolDraft.weeks("RHD", "RHD (Viral Hemorajik Hastalık)", 10, 1, 21, 365));
    }
}
