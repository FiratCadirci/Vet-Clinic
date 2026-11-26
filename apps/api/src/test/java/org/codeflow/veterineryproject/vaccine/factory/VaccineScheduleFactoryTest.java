package org.codeflow.veterineryproject.vaccine.factory;

import org.codeflow.veterineryproject.vaccine.dto.ProtocolDoseResponse;
import org.codeflow.veterineryproject.vaccine.dto.ProtocolResponse;
import org.codeflow.veterineryproject.vaccine.dto.VaccineScheduleTemplate;
import org.codeflow.veterineryproject.vaccine.factory.VaccineScheduleFactory.AnimalProfile;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class VaccineScheduleFactoryTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 1);

    private final VaccineScheduleFactories factories = new VaccineScheduleFactories(
            new DogVaccineScheduleFactory(), new CatVaccineScheduleFactory(), new GenericVaccineScheduleFactory());

    @Test
    void factory_picksSpeciesSpecificImplementation() {
        assertThat(factories.forSpecies("DOG")).isInstanceOf(DogVaccineScheduleFactory.class);
        assertThat(factories.forSpecies("cat")).isInstanceOf(CatVaccineScheduleFactory.class);
        assertThat(factories.forSpecies("RABBIT")).isInstanceOf(GenericVaccineScheduleFactory.class);
    }

    @Test
    void template_onlyContainsApprovedProtocolsOfTheAnimalsSpecies() {
        List<ProtocolResponse> protocols = List.of(
                protocol("DOG-CORE", "DOG", "APPROVED", dose(1, null, null), dose(2, null, 21)),
                protocol("DOG-DRAFT", "DOG", "DRAFT", dose(1, null, null)),
                protocol("CAT-CORE", "CAT", "APPROVED", dose(1, null, null)));

        VaccineScheduleTemplate template = factories.forSpecies("DOG")
                .create(new AnimalProfile(UUID.randomUUID(), "DOG", TODAY.minusDays(200)), protocols, TODAY);

        assertThat(template.entries()).extracting(VaccineScheduleTemplate.Entry::protocolCode).containsOnly("DOG-CORE");
        assertThat(template.entries()).hasSize(2);
        assertThat(template.ageDays()).isEqualTo(200);
    }

    @Test
    void template_appliesSpeciesMinimumAgeForFirstDose() {
        ProtocolResponse dog = protocol("CORE", "DOG", "APPROVED", dose(1, null, null), dose(2, null, 21));
        ProtocolResponse cat = protocol("CORE", "CAT", "APPROVED", dose(1, null, null));

        VaccineScheduleTemplate puppy = factories.forSpecies("DOG")
                .create(new AnimalProfile(UUID.randomUUID(), "DOG", TODAY.minusDays(30)), List.of(dog), TODAY);
        VaccineScheduleTemplate kitten = factories.forSpecies("CAT")
                .create(new AnimalProfile(UUID.randomUUID(), "CAT", TODAY.minusDays(50)), List.of(cat), TODAY);

        assertThat(puppy.entries().get(0).minimumAgeDays()).isEqualTo(42);
        assertThat(puppy.entries().get(0).eligibleNow()).isFalse();
        assertThat(puppy.entries().get(0).earliestDate()).isEqualTo(TODAY.minusDays(30).plusDays(42));
        assertThat(kitten.entries().get(0).minimumAgeDays()).isEqualTo(56);
        assertThat(kitten.entries().get(0).eligibleNow()).isFalse();
    }

    @Test
    void template_adultAnimalIsEligibleAndUnknownBirthDateIsNotRestricted() {
        ProtocolResponse dog = protocol("CORE", "DOG", "APPROVED", dose(1, null, null));

        VaccineScheduleTemplate adult = factories.forSpecies("DOG")
                .create(new AnimalProfile(UUID.randomUUID(), "DOG", TODAY.minusYears(3)), List.of(dog), TODAY);
        VaccineScheduleTemplate unknown = factories.forSpecies("DOG")
                .create(new AnimalProfile(UUID.randomUUID(), "DOG", null), List.of(dog), TODAY);

        assertThat(adult.entries().get(0).eligibleNow()).isTrue();
        assertThat(unknown.entries().get(0).eligibleNow()).isTrue();
        assertThat(unknown.ageDays()).isNull();
    }

    @Test
    void nextDose_usesNextDoseIntervalThenBooster() {
        ProtocolResponse p = protocol("CORE", "DOG", "APPROVED", 365, dose(1, null, null), dose(2, null, 21), dose(3, null, 28));
        LocalDate given = LocalDate.of(2026, 1, 10);

        assertThat(NextDoseCalculator.compute(given, p, 1)).contains(given.plusDays(21));
        assertThat(NextDoseCalculator.compute(given, p, 2)).contains(given.plusDays(28));
        assertThat(NextDoseCalculator.compute(given, p, 3)).contains(given.plusDays(365));
    }

    @Test
    void nextDose_isEmptyWithoutBoosterOrProtocol() {
        ProtocolResponse p = protocol("CORE", "DOG", "APPROVED", dose(1, null, null));

        assertThat(NextDoseCalculator.compute(TODAY, p, 1)).isEmpty();
        assertThat(NextDoseCalculator.compute(TODAY, null, 1)).isEmpty();
    }

    private static ProtocolDoseResponse dose(int number, Integer age, Integer interval) {
        return new ProtocolDoseResponse(UUID.randomUUID(), number, "Aşı " + number, age, interval, null);
    }

    private static ProtocolResponse protocol(String code, String species, String status, ProtocolDoseResponse... doses) {
        return protocol(code, species, status, null, doses);
    }

    private static ProtocolResponse protocol(String code, String species, String status, Integer booster, ProtocolDoseResponse... doses) {
        return new ProtocolResponse(UUID.randomUUID(), code, code, UUID.randomUUID(), species, 1, status, booster, null, null, null, List.of(doses));
    }
}
