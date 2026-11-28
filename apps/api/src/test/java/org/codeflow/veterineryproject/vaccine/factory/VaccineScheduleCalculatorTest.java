package org.codeflow.veterineryproject.vaccine.factory;

import org.codeflow.veterineryproject.vaccine.dto.AnimalVaccineScheduleResponse.Item;
import org.codeflow.veterineryproject.vaccine.dto.ProtocolDoseResponse;
import org.codeflow.veterineryproject.vaccine.dto.ProtocolResponse;
import org.codeflow.veterineryproject.vaccine.dto.VaccineScheduleTemplate;
import org.codeflow.veterineryproject.vaccine.factory.VaccineScheduleCalculator.GivenVaccination;
import org.codeflow.veterineryproject.vaccine.factory.VaccineScheduleCalculator.Result;
import org.codeflow.veterineryproject.vaccine.factory.VaccineScheduleFactory.AnimalProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** FG-D4: sonraki doz zinciri gerçekten yapılan önceki dozdan hesaplanır; rapel ve en yakın vade. DB'siz. */
@DisplayName("Aşı takvimi hesabı")
class VaccineScheduleCalculatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 1);
    private static final LocalDate BIRTH = TODAY.minusDays(120);

    private final VaccineScheduleFactory dogFactory = new DogVaccineScheduleFactory();
    private final ProtocolResponse core = protocol("DOG-CORE", "Karma", 365, dose(1, 56, null), dose(2, null, 21), dose(3, null, 21));

    @Test
    @DisplayName("ikinci doz, ilk dozun planlanan değil gerçekten yapıldığı tarihten hesaplanır")
    void testCalculateGivenLateFirstDoseChainsFromActualDate() {
        LocalDate firstGiven = BIRTH.plusDays(80);

        Item item = single(calculate(List.of(given("DOG-CORE", "Karma", firstGiven, null))));

        assertThat(item.doses().get(0).status()).isEqualTo(VaccineScheduleCalculator.DONE);
        assertThat(item.doses().get(0).plannedAt()).isEqualTo(BIRTH.plusDays(56));
        assertThat(item.doses().get(1).plannedAt()).isEqualTo(firstGiven.plusDays(21));
        assertThat(item.doses().get(1).status()).isEqualTo(VaccineScheduleCalculator.OVERDUE);
        assertThat(item.next().doseNumber()).isEqualTo(2);
        assertThat(item.next().booster()).isFalse();
    }

    @Test
    @DisplayName("yapılmamış dozdan sonraki doz tarihsiz planlı kalır, gecikmiş sayılmaz")
    void testCalculateGivenMissingDoseLeavesFollowingDoseUndated() {
        Item item = single(calculate(List.of()));

        assertThat(item.doses().get(0).status()).isEqualTo(VaccineScheduleCalculator.OVERDUE);
        assertThat(item.doses().get(1).plannedAt()).isNull();
        assertThat(item.doses().get(1).status()).isEqualTo(VaccineScheduleCalculator.PLANNED);
        assertThat(item.doses().get(2).plannedAt()).isNull();
    }

    @Test
    @DisplayName("seri tamamlanınca rapel son dozdan hesaplanır; hekimin verdiği vade önceliklidir")
    void testCalculateGivenCompletedSeriesComputesBooster() {
        LocalDate d1 = BIRTH.plusDays(56);
        LocalDate d2 = d1.plusDays(25);
        LocalDate d3 = d2.plusDays(21);

        Item item = single(calculate(List.of(given("DOG-CORE", "Karma", d3, null), given("DOG-CORE", "Karma", d1, null),
                given("DOG-CORE", "Karma", d2, null))));
        Item overridden = single(calculate(List.of(given("DOG-CORE", "Karma", d1, null), given("DOG-CORE", "Karma", d2, null),
                given("DOG-CORE", "Karma", d3, d3.plusDays(200)))));

        assertThat(item.doses()).allMatch(d -> d.status().equals(VaccineScheduleCalculator.DONE));
        assertThat(item.doses().get(2).plannedAt()).isEqualTo(d2.plusDays(21));
        assertThat(item.booster().dueAt()).isEqualTo(d3.plusDays(365));
        assertThat(item.next().booster()).isTrue();
        assertThat(item.next().doseNumber()).isEqualTo(4);
        assertThat(overridden.booster().dueAt()).isEqualTo(d3.plusDays(200));
    }

    @Test
    @DisplayName("protokol dışı aşılar ayrı listelenir, adı eşleşen protokolsüz kayıt seriye sayılır, en yakın vade seçilir")
    void testCalculateGivenFreeVaccinationsListsOthersAndPicksNearestDue() {
        Result result = calculate(List.of(given(null, "karma", BIRTH.plusDays(60), null),
                given(null, "Lyme", TODAY.minusDays(300), TODAY.minusDays(10)),
                given(null, "Lyme", TODAY.minusDays(5), TODAY.plusDays(360))));

        assertThat(single(result).doses().get(0).status()).isEqualTo(VaccineScheduleCalculator.DONE);
        assertThat(result.others()).hasSize(2);
        assertThat(result.nextDue().vaccineName()).isEqualTo("Karma");
        assertThat(result.nextDue().dueAt()).isEqualTo(BIRTH.plusDays(81));
        assertThat(result.nextDue().overdue()).isTrue();
    }

    private Result calculate(List<GivenVaccination> given) {
        VaccineScheduleTemplate template = dogFactory.create(new AnimalProfile(UUID.randomUUID(), "DOG", BIRTH), List.of(core), TODAY);
        return VaccineScheduleCalculator.calculate(template, given, TODAY);
    }

    private static Item single(Result result) {
        assertThat(result.items()).hasSize(1);
        return result.items().getFirst();
    }

    private static GivenVaccination given(String code, String name, LocalDate date, LocalDate nextDue) {
        return new GivenVaccination(UUID.randomUUID(), code, name, OffsetDateTime.of(date.atTime(10, 0), ZoneOffset.ofHours(3)), nextDue);
    }

    private static ProtocolDoseResponse dose(int number, Integer age, Integer interval) {
        return new ProtocolDoseResponse(UUID.randomUUID(), number, "Karma", age, interval, null);
    }

    private static ProtocolResponse protocol(String code, String name, Integer booster, ProtocolDoseResponse... doses) {
        return new ProtocolResponse(UUID.randomUUID(), code, name, UUID.randomUUID(), "DOG", 1, "APPROVED", booster, null,
                UUID.randomUUID(), OffsetDateTime.now(), List.of(doses));
    }
}
