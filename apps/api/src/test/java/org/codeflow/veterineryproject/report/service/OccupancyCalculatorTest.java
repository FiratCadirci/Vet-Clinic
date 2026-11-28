package org.codeflow.veterineryproject.report.service;

import org.codeflow.veterineryproject.report.dto.ReportDto.OccupancyRow;
import org.codeflow.veterineryproject.report.model.BookedMinutes;
import org.codeflow.veterineryproject.report.model.ReportPeriod;
import org.codeflow.veterineryproject.report.model.WorkingHoursSlot;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Doluluk hesabı (FG-L1) ve rapor aralığı: veritabanı olmadan. */
class OccupancyCalculatorTest {

    private static final UUID B1 = UUID.randomUUID();
    private static final UUID B2 = UUID.randomUUID();
    private static final UUID V1 = UUID.randomUUID();
    private static final UUID V2 = UUID.randomUUID();
    /** 9 Kasım 2026 Pazartesi; 2 tam hafta. */
    private static final LocalDate FROM = LocalDate.of(2026, 11, 9);
    private static final LocalDate TO = LocalDate.of(2026, 11, 23);

    private final OccupancyCalculator calc = new OccupancyCalculator();

    private static WorkingHoursSlot slot(UUID branch, UUID vet, int dow, String from, String to) {
        return new WorkingHoursSlot(branch, vet, "Dr", dow, LocalTime.parse(from), LocalTime.parse(to),
                LocalDate.of(2026, 1, 1), null);
    }

    @Test
    void availableMinutes_areDurationTimesMatchingDays_summedPerVetAndBranch() {
        List<OccupancyRow> rows = calc.compute(
                List.of(slot(B1, V1, 1, "09:00", "17:00"), slot(B1, V1, 3, "09:00", "12:00")),
                List.of(new BookedMinutes(B1, V1, 264)), FROM, TO);
        assertThat(rows).hasSize(1);
        OccupancyRow r = rows.get(0);
        assertThat(r.availableMinutes()).isEqualTo((480 + 180) * 2);
        assertThat(r.bookedMinutes()).isEqualTo(264);
        assertThat(r.occupancyRate()).isEqualByComparingTo("20.0");
    }

    @Test
    void sundayIsDayZero_likePostgresDow() {
        List<OccupancyRow> rows = calc.compute(List.of(slot(B1, V1, 0, "10:00", "12:00")), List.of(), FROM, TO);
        assertThat(rows.get(0).availableMinutes()).isEqualTo(2 * 120);
    }

    @Test
    void effectiveDates_limitDays() {
        WorkingHoursSlot limited = new WorkingHoursSlot(B1, V1, "Dr", 1, LocalTime.of(9, 0), LocalTime.of(10, 0),
                LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 30));
        WorkingHoursSlot ended = new WorkingHoursSlot(B1, V2, "Dr", 1, LocalTime.of(9, 0), LocalTime.of(10, 0),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 11, 9));
        List<OccupancyRow> rows = calc.compute(List.of(limited, ended), List.of(), FROM, TO);
        assertThat(rows).extracting(OccupancyRow::veterinarianId, OccupancyRow::availableMinutes)
                .containsExactlyInAnyOrder(org.assertj.core.groups.Tuple.tuple(V1, 60L),
                        org.assertj.core.groups.Tuple.tuple(V2, 60L));
    }

    @Test
    void sameVetInTwoBranches_isTwoRows_andBookingsWithoutHoursAreIgnored() {
        List<OccupancyRow> rows = calc.compute(
                List.of(slot(B1, V1, 1, "09:00", "10:00"), slot(B2, V1, 2, "09:00", "10:00")),
                List.of(new BookedMinutes(B2, V1, 30), new BookedMinutes(B1, V2, 60)), FROM, TO);
        assertThat(rows).extracting(OccupancyRow::clinicId).containsExactlyInAnyOrder(B1, B2);
        assertThat(rows.stream().filter(r -> r.clinicId().equals(B2)).findFirst().orElseThrow().occupancyRate())
                .isEqualByComparingTo("25.0");
        assertThat(rows.stream().mapToLong(OccupancyRow::bookedMinutes).sum()).isEqualTo(30);
    }

    @Test
    void noCapacity_rateIsZero_andRoundingIsHalfUp() {
        List<OccupancyRow> rows = calc.compute(List.of(slot(B1, V1, 6, "09:00", "13:00")),
                List.of(new BookedMinutes(B1, V1, 30)), FROM, LocalDate.of(2026, 11, 12));
        assertThat(rows.get(0).availableMinutes()).isZero();
        assertThat(rows.get(0).occupancyRate()).isEqualByComparingTo("0");
        assertThat(calc.rate(30, 480)).isEqualByComparingTo("6.3");
        assertThat(calc.rate(1, 3)).isEqualByComparingTo("33.3");
        assertThat(calc.rate(480, 480)).isEqualByComparingTo("100.0");
        assertThat(calc.compute(List.of(), List.of(new BookedMinutes(B1, V1, 30)), FROM, TO)).isEmpty();
    }

    @Test
    void totalRate_isOverAllRows() {
        List<OccupancyRow> rows = List.of(
                new OccupancyRow(B1, V1, "a", 3120, 140, calc.rate(140, 3120)),
                new OccupancyRow(B2, V2, "b", 1680, 150, calc.rate(150, 1680)));
        assertThat(calc.totalRate(rows)).isEqualByComparingTo("6.0");
    }

    @Test
    void reportPeriod_localDays_defaultsAndLimits() {
        ReportPeriod march = new ReportPeriod(OffsetDateTime.parse("2026-03-01T00:00:00+03:00"),
                OffsetDateTime.parse("2026-04-01T00:00:00+03:00"));
        assertThat(march.firstDay()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(march.endDayExclusive()).isEqualTo(LocalDate.of(2026, 4, 1));
        ReportPeriod partial = ReportPeriod.parse("2026-03-01T00:00:00Z", "2026-03-02T10:00:00Z", OffsetDateTime.now());
        assertThat(partial.firstDay()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(partial.endDayExclusive()).isEqualTo(LocalDate.of(2026, 3, 3));
        OffsetDateTime now = OffsetDateTime.parse("2026-11-15T12:00:00+03:00");
        assertThat(ReportPeriod.parse(null, null, now).from()).isEqualTo(now.minusDays(30));
        assertThatThrownBy(() -> ReportPeriod.parse("2026-03-02 00:00", "2026-03-01 00:00", now))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ReportPeriod.parse("2025-01-01 00:00", "2026-03-01 00:00", now))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
