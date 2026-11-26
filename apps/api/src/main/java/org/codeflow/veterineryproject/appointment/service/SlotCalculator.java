package org.codeflow.veterineryproject.appointment.service;

import org.codeflow.veterineryproject.appointment.model.BusyInterval;
import org.codeflow.veterineryproject.appointment.model.Slot;
import org.codeflow.veterineryproject.appointment.model.WorkingWindow;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Uygun randevu saatlerini hesaplayan saf alan servisi (FG-E2). Veritabanına ve Spring'e bağımlı değildir;
 * çalışma saatleri, dolu aralıklar ve "şimdi" dışarıdan verilir, böylece birim testlerle doğrulanır.
 * Kural: başlangıçlar çalışma penceresinin başından itibaren {@code step} aralıkla dizilir; hizmet süresi
 * pencereye sığmalı, başlangıç geçmişte olmamalı ve hiçbir dolu aralıkla kesişmemelidir ([başlangıç, bitiş) yarı açık).
 */
public final class SlotCalculator {

    /** Varsayılan ızgara aralığı (eski sistemle aynı: 15 dakika). */
    public static final Duration DEFAULT_STEP = Duration.ofMinutes(15);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final Duration step;

    public SlotCalculator() {
        this(DEFAULT_STEP);
    }

    public SlotCalculator(Duration step) {
        if (step == null || step.isZero() || step.isNegative()) {
            throw new IllegalArgumentException("Slot aralığı pozitif olmalıdır.");
        }
        this.step = step;
    }

    public List<Slot> calculate(LocalDate date, ZoneId zone, List<WorkingWindow> windows, Duration duration,
                                List<BusyInterval> busy, Instant now) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("Hizmet süresi pozitif olmalıdır.");
        }
        List<Slot> slots = new ArrayList<>();
        List<WorkingWindow> ordered = windows.stream().sorted(Comparator.comparing(WorkingWindow::startsAt)).toList();
        for (WorkingWindow window : ordered) {
            ZonedDateTime windowEnd = ZonedDateTime.of(date, window.endsAt(), zone);
            for (ZonedDateTime start = ZonedDateTime.of(date, window.startsAt(), zone);
                 !start.plus(duration).isAfter(windowEnd); start = start.plus(step)) {
                ZonedDateTime end = start.plus(duration);
                if (start.toInstant().isBefore(now) || overlapsAny(start, end, busy)) {
                    continue;
                }
                Slot slot = new Slot(start.toOffsetDateTime(), end.toOffsetDateTime(), TIME.format(start));
                if (slots.stream().noneMatch(s -> s.startsAt().isEqual(slot.startsAt()))) {
                    slots.add(slot);
                }
            }
        }
        slots.sort(Comparator.comparing(Slot::startsAt));
        return slots;
    }

    private static boolean overlapsAny(ZonedDateTime start, ZonedDateTime end, List<BusyInterval> busy) {
        for (BusyInterval b : busy) {
            if (b.startsAt().toInstant().isBefore(end.toInstant()) && b.endsAt().toInstant().isAfter(start.toInstant())) {
                return true;
            }
        }
        return false;
    }
}
