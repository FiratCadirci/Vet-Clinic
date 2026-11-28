package org.codeflow.veterineryproject.vaccine.factory;

import org.codeflow.veterineryproject.vaccine.dto.AnimalVaccineScheduleResponse.Booster;
import org.codeflow.veterineryproject.vaccine.dto.AnimalVaccineScheduleResponse.Dose;
import org.codeflow.veterineryproject.vaccine.dto.AnimalVaccineScheduleResponse.Item;
import org.codeflow.veterineryproject.vaccine.dto.AnimalVaccineScheduleResponse.Next;
import org.codeflow.veterineryproject.vaccine.dto.AnimalVaccineScheduleResponse.NextDue;
import org.codeflow.veterineryproject.vaccine.dto.AnimalVaccineScheduleResponse.OtherVaccination;
import org.codeflow.veterineryproject.vaccine.dto.VaccineScheduleTemplate;
import org.codeflow.veterineryproject.vaccine.dto.VaccineScheduleTemplate.Entry;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Hastanın aşı takvimi hesabı (saf, DB'siz; birim testlerle doğrulanır). Fabrikanın ürettiği şablon
 * (onaylı protokoller + türün en erken yaş kuralı) ile hastanın gerçek aşı kayıtları birleştirilir.
 *
 * Seri dozları zincirdir: n. dozun tarihi (n-1). dozun GERÇEKTEN yapıldığı tarihten hesaplanır. Önceki doz
 * yapılmadıysa sonraki doz tarihsiz "planlı" kalır (planlanmış bir tarihten zincirlemek gecikmeyi yanlış gösterir).
 */
public final class VaccineScheduleCalculator {

    public static final String DONE = "DONE";
    public static final String PLANNED = "PLANNED";
    public static final String OVERDUE = "OVERDUE";
    private static final ZoneId ZONE = ZoneId.of("Europe/Istanbul");

    /** Hastanın aşı kaydı; protocolCode, kayıt bir protokol dozuna bağlıysa doludur. */
    public record GivenVaccination(UUID recordId, String protocolCode, String vaccineName, OffsetDateTime administeredAt,
                                   LocalDate nextDueAt) {
    }

    public record Result(List<Item> items, List<OtherVaccination> others, NextDue nextDue) {
    }

    private VaccineScheduleCalculator() {
    }

    /** @param given hastanın aşı kayıtları (sırası önemsiz) */
    public static Result calculate(VaccineScheduleTemplate template, List<GivenVaccination> given, LocalDate today) {
        List<GivenVaccination> chronological = given.stream()
                .sorted(Comparator.comparing(GivenVaccination::administeredAt)).toList();
        Set<UUID> used = new HashSet<>();
        List<Item> items = new ArrayList<>();
        for (List<Entry> protocol : byProtocol(template.entries()).values()) {
            List<GivenVaccination> done = chronological.stream().filter(v -> matches(v, protocol)).toList();
            done.forEach(v -> used.add(v.recordId()));
            items.add(item(protocol, done, today));
        }
        List<OtherVaccination> others = chronological.stream().filter(v -> !used.contains(v.recordId()))
                .map(v -> new OtherVaccination(v.recordId(), v.vaccineName(), v.administeredAt(), v.nextDueAt())).toList();
        return new Result(items, others, nextDue(items, others, chronological, today));
    }

    private static Item item(List<Entry> protocol, List<GivenVaccination> done, LocalDate today) {
        Entry first = protocol.getFirst();
        List<Dose> doses = new ArrayList<>();
        LocalDate previousGiven = null;
        for (int i = 0; i < protocol.size(); i++) {
            Entry entry = protocol.get(i);
            LocalDate planned = i == 0 ? entry.earliestDate() : chained(previousGiven, entry.intervalFromPreviousDays());
            if (i < done.size()) {
                GivenVaccination v = done.get(i);
                doses.add(new Dose(entry.doseNumber(), DONE, planned, v.administeredAt(), v.recordId()));
                previousGiven = localDate(v.administeredAt());
            } else {
                boolean late = planned != null && planned.isBefore(today);
                doses.add(new Dose(entry.doseNumber(), late ? OVERDUE : PLANNED, planned, null, null));
                previousGiven = null;
            }
        }
        Booster booster = booster(first.boosterIntervalDays(), protocol.size(), done, today);
        Next next = doses.stream().filter(d -> !DONE.equals(d.status())).findFirst()
                .map(d -> new Next(d.doseNumber(), d.plannedAt(), OVERDUE.equals(d.status()), false))
                .orElseGet(() -> booster == null ? null : new Next(done.size() + 1, booster.dueAt(), booster.overdue(), true));
        return new Item(first.protocolId(), first.protocolCode(), first.protocolName(), protocol.size(), doses, booster, next);
    }

    private static Booster booster(Integer intervalDays, int doseCount, List<GivenVaccination> done, LocalDate today) {
        if (done.size() < doseCount) {
            return null;
        }
        GivenVaccination last = done.getLast();
        LocalDate due = last.nextDueAt() != null ? last.nextDueAt()
                : intervalDays == null ? null : localDate(last.administeredAt()).plusDays(intervalDays);
        return due == null ? null : new Booster(due, due.isBefore(today));
    }

    private static NextDue nextDue(List<Item> items, List<OtherVaccination> others, List<GivenVaccination> all, LocalDate today) {
        Stream<NextDue> planned = items.stream().filter(i -> i.next() != null && i.next().dueAt() != null)
                .map(i -> new NextDue(i.vaccineName(), i.next().doseNumber(), i.next().dueAt(), i.next().dueAt().isBefore(today)));
        Stream<NextDue> free = others.stream().filter(o -> o.nextDueAt() != null && !supersededLater(o, all))
                .map(o -> new NextDue(o.vaccineName(), doseIndex(o, all) + 1, o.nextDueAt(), o.nextDueAt().isBefore(today)));
        return Stream.concat(planned, free).min(Comparator.comparing(NextDue::dueAt)).orElse(null);
    }

    /** Aynı adlı daha yeni bir kayıt eski vadeyi kapatır. */
    private static boolean supersededLater(OtherVaccination o, List<GivenVaccination> all) {
        return all.stream().anyMatch(v -> sameName(v.vaccineName(), o.vaccineName()) && v.administeredAt().isAfter(o.administeredAt()));
    }

    private static int doseIndex(OtherVaccination o, List<GivenVaccination> all) {
        return (int) all.stream().filter(v -> sameName(v.vaccineName(), o.vaccineName())
                && !v.administeredAt().isAfter(o.administeredAt())).count();
    }

    private static boolean matches(GivenVaccination v, List<Entry> protocol) {
        Entry first = protocol.getFirst();
        if (v.protocolCode() != null) {
            return v.protocolCode().equals(first.protocolCode());
        }
        return sameName(v.vaccineName(), first.protocolName())
                || protocol.stream().anyMatch(e -> sameName(v.vaccineName(), e.vaccineName()));
    }

    private static Map<String, List<Entry>> byProtocol(List<Entry> entries) {
        Map<String, List<Entry>> grouped = new LinkedHashMap<>();
        entries.stream().sorted(Comparator.comparing(Entry::protocolCode).thenComparingInt(Entry::doseNumber))
                .forEach(e -> grouped.computeIfAbsent(e.protocolCode(), k -> new ArrayList<>()).add(e));
        return grouped;
    }

    private static LocalDate chained(LocalDate previousGiven, Integer intervalDays) {
        return previousGiven == null ? null : previousGiven.plusDays(Optional.ofNullable(intervalDays).orElse(0));
    }

    private static LocalDate localDate(OffsetDateTime value) {
        return value.atZoneSameInstant(ZONE).toLocalDate();
    }

    private static boolean sameName(String a, String b) {
        return a != null && b != null && a.trim().toLowerCase(Locale.ROOT).equals(b.trim().toLowerCase(Locale.ROOT));
    }
}
