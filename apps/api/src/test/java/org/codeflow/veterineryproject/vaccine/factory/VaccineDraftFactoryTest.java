package org.codeflow.veterineryproject.vaccine.factory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Factory Method (FG-D1): her tür fabrikası kendi varsayılan, onaysız aşı takvimi taslağını üretir; DB'siz. */
@DisplayName("Tür bazında aşı şablonu taslağı (Factory Method)")
class VaccineDraftFactoryTest {

    private final VaccineScheduleFactories factories = new VaccineScheduleFactories(new DogVaccineScheduleFactory(),
            new CatVaccineScheduleFactory(), new RabbitVaccineScheduleFactory(), new BirdVaccineScheduleFactory(),
            new GenericVaccineScheduleFactory());

    @Test
    @DisplayName("dört tür için ayrı fabrika seçilir, diğer türler genel fabrikaya düşer")
    void testForSpeciesGivenFourSpeciesReturnsSpecificFactories() {
        assertThat(factories.forSpecies("DOG")).isInstanceOf(DogVaccineScheduleFactory.class);
        assertThat(factories.forSpecies("cat")).isInstanceOf(CatVaccineScheduleFactory.class);
        assertThat(factories.forSpecies("RABBIT")).isInstanceOf(RabbitVaccineScheduleFactory.class);
        assertThat(factories.forSpecies("BIRD")).isInstanceOf(BirdVaccineScheduleFactory.class);
        assertThat(factories.forSpecies("REPTILE")).isInstanceOf(GenericVaccineScheduleFactory.class);
    }

    @Test
    @DisplayName("köpek taslağı: karma 8. hafta, 21 gün arayla 3 doz, yıllık rapel; kuduz 12. hafta")
    void testCreateDraftGivenDogReturnsCoreSeries() {
        List<ProtocolDraft> dog = factories.forSpecies("DOG").createDraft();

        ProtocolDraft core = byCode(dog, "CORE");
        assertThat(core.firstDoseAgeDays()).isEqualTo(56);
        assertThat(core.doseCount()).isEqualTo(3);
        assertThat(core.doseIntervalDays()).isEqualTo(21);
        assertThat(core.boosterIntervalDays()).isEqualTo(365);
        assertThat(byCode(dog, "RABIES").firstDoseAgeDays()).isEqualTo(84);
    }

    @Test
    @DisplayName("kedi, tavşan ve kuş farklı taslaklar üretir; liste ilk doz yaşına göre sıralıdır")
    void testCreateDraftGivenOtherSpeciesReturnsDistinctSortedSchedules() {
        List<ProtocolDraft> cat = factories.forSpecies("CAT").createDraft();
        List<ProtocolDraft> rabbit = factories.forSpecies("RABBIT").createDraft();
        List<ProtocolDraft> bird = factories.forSpecies("BIRD").createDraft();

        assertThat(byCode(cat, "CORE").doseCount()).isEqualTo(2);
        assertThat(cat).extracting(ProtocolDraft::code).contains("FELV").doesNotContain("CORONA");
        assertThat(rabbit).extracting(ProtocolDraft::code).containsExactly("MYXO", "PARASITE", "RHD");
        assertThat(byCode(rabbit, "MYXO").boosterIntervalDays()).isEqualTo(180);
        assertThat(bird).singleElement().satisfies(d -> assertThat(d.boosterIntervalDays()).isEqualTo(180));
        assertThat(cat).isSortedAccordingTo((a, b) -> Integer.compare(a.firstDoseAgeDays(), b.firstDoseAgeDays()));
    }

    @Test
    @DisplayName("hazır takvimi olmayan tür boş taslak üretir")
    void testCreateDraftGivenGenericSpeciesReturnsEmpty() {
        assertThat(factories.forSpecies("OTHER").createDraft()).isEmpty();
    }

    private static ProtocolDraft byCode(List<ProtocolDraft> drafts, String code) {
        return drafts.stream().filter(d -> d.code().equals(code)).findFirst().orElseThrow();
    }
}
