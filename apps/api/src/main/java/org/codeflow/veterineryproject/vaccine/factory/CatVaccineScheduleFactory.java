package org.codeflow.veterineryproject.vaccine.factory;

import org.springframework.stereotype.Component;

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
}
