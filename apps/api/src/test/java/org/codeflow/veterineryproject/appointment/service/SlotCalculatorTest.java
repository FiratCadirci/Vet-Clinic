package org.codeflow.veterineryproject.appointment.service;

import org.codeflow.veterineryproject.appointment.model.BusyInterval;
import org.codeflow.veterineryproject.appointment.model.Slot;
import org.codeflow.veterineryproject.appointment.model.WorkingWindow;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** FG-E2 uygun saat hesabı (veritabanı olmadan). */
class SlotCalculatorTest {

    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");
    private static final LocalDate DAY = LocalDate.of(2035, 3, 6);
    private static final Instant LONG_AGO = Instant.parse("2020-01-01T00:00:00Z");
    private final SlotCalculator calculator = new SlotCalculator();

    private static WorkingWindow window(String from, String to) {
        return new WorkingWindow(LocalTime.parse(from), LocalTime.parse(to));
    }

    private static OffsetDateTime at(int hour, int minute) {
        return DAY.atTime(hour, minute).atZone(ISTANBUL).toOffsetDateTime();
    }

    private static List<String> times(List<Slot> slots) {
        return slots.stream().map(Slot::time).toList();
    }

    @Test
    void slotsStartEvery15MinutesAndTheServiceMustFitTheWindow() {
        List<Slot> slots = calculator.calculate(DAY, ISTANBUL, List.of(window("09:00", "10:00")), Duration.ofMinutes(30),
                List.of(), LONG_AGO);
        assertThat(times(slots)).containsExactly("09:00", "09:15", "09:30");
        assertThat(slots.getFirst().startsAt()).isEqualTo(OffsetDateTime.parse("2035-03-06T09:00:00+03:00"));
        assertThat(slots.getFirst().endsAt()).isEqualTo(OffsetDateTime.parse("2035-03-06T09:30:00+03:00"));
    }

    @Test
    void timeOutsideWorkingWindowsIsExcluded() {
        List<Slot> slots = calculator.calculate(DAY, ISTANBUL, List.of(window("13:00", "14:00"), window("09:00", "10:00")),
                Duration.ofMinutes(45), List.of(), LONG_AGO);
        // Öğle arası (10:00-13:00) ve pencereye sığmayan 09:30 / 13:30 başlangıçları yok; sonuç sıralıdır.
        assertThat(times(slots)).containsExactly("09:00", "09:15", "13:00", "13:15");
    }

    @Test
    void busyIntervalsBlockOverlappingStartsButAdjacentSlotsStayFree() {
        List<BusyInterval> busy = List.of(new BusyInterval(at(9, 30), at(10, 0)));
        List<Slot> slots = calculator.calculate(DAY, ISTANBUL, List.of(window("09:00", "11:00")), Duration.ofMinutes(30),
                busy, LONG_AGO);
        assertThat(times(slots)).contains("09:00", "10:00").doesNotContain("09:15", "09:30", "09:45");
    }

    @Test
    void busyIntervalInAnotherOffsetIsComparedByInstant() {
        // 06:30Z = 09:30 İstanbul
        List<BusyInterval> busy = List.of(new BusyInterval(OffsetDateTime.parse("2035-03-06T06:30:00Z"),
                OffsetDateTime.parse("2035-03-06T07:00:00Z")));
        List<Slot> slots = calculator.calculate(DAY, ISTANBUL, List.of(window("09:00", "11:00")), Duration.ofMinutes(30),
                busy, LONG_AGO);
        assertThat(times(slots)).doesNotContain("09:30");
    }

    @Test
    void pastStartsAreExcluded() {
        Instant now = at(9, 20).toInstant();
        List<Slot> slots = calculator.calculate(DAY, ISTANBUL, List.of(window("09:00", "10:30")), Duration.ofMinutes(30),
                List.of(), now);
        assertThat(times(slots)).containsExactly("09:30", "09:45", "10:00");
        assertThat(calculator.calculate(DAY, ISTANBUL, List.of(window("09:00", "10:30")), Duration.ofMinutes(30),
                List.of(), at(23, 0).toInstant())).isEmpty();
    }

    @Test
    void noWorkingHoursMeansNoSlots_andInvalidDurationIsRejected() {
        assertThat(calculator.calculate(DAY, ISTANBUL, List.of(), Duration.ofMinutes(30), List.of(), LONG_AGO)).isEmpty();
        assertThatThrownBy(() -> calculator.calculate(DAY, ISTANBUL, List.of(), Duration.ZERO, List.of(), LONG_AGO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SlotCalculator(Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void overlappingWindowsDoNotProduceDuplicateSlots() {
        List<Slot> slots = calculator.calculate(DAY, ISTANBUL, List.of(window("09:00", "10:00"), window("09:30", "10:30")),
                Duration.ofMinutes(30), List.of(), LONG_AGO);
        assertThat(times(slots)).containsExactly("09:00", "09:15", "09:30", "09:45", "10:00");
    }
}
