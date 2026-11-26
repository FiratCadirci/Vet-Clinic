package org.codeflow.veterineryproject.vaccine.factory;

import org.springframework.stereotype.Component;

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
}
