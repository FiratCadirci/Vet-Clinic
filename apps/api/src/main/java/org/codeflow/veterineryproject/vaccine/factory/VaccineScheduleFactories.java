package org.codeflow.veterineryproject.vaccine.factory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/** Türe uygun fabrikayı seçer; hiçbiri uymazsa genel fabrika kullanılır. */
@Component
public class VaccineScheduleFactories {

    private final List<VaccineScheduleFactory> specific;
    private final GenericVaccineScheduleFactory generic;

    @Autowired
    public VaccineScheduleFactories(DogVaccineScheduleFactory dog, CatVaccineScheduleFactory cat,
                                    RabbitVaccineScheduleFactory rabbit, BirdVaccineScheduleFactory bird,
                                    GenericVaccineScheduleFactory generic) {
        this.specific = List.of(dog, cat, rabbit, bird);
        this.generic = generic;
    }

    /** Yalnız köpek ve kedi fabrikasıyla (diğer türler genel fabrikaya düşer). */
    public VaccineScheduleFactories(DogVaccineScheduleFactory dog, CatVaccineScheduleFactory cat, GenericVaccineScheduleFactory generic) {
        this.specific = List.of(dog, cat);
        this.generic = generic;
    }

    public VaccineScheduleFactory forSpecies(String speciesCode) {
        return specific.stream().filter(f -> f.supports(speciesCode)).findFirst().orElse(generic);
    }
}
