package org.codeflow.veterineryproject.vaccine.factory;

import org.springframework.stereotype.Component;

import java.util.List;

/** Özel fabrikası olmayan türler için yedek; yaş kısıtı yalnızca şablondaki önerilen yaştan gelir. */
@Component
public class GenericVaccineScheduleFactory extends AbstractSpeciesScheduleFactory {

    @Override
    public boolean supports(String speciesCode) {
        return true;
    }

    @Override
    protected int minimumFirstDoseAgeDays() {
        return 0;
    }

    /** Hazır takvim yok: klinik kalemleri kendisi girer. */
    @Override
    protected List<ProtocolDraft> defaultSchedule() {
        return List.of();
    }
}
