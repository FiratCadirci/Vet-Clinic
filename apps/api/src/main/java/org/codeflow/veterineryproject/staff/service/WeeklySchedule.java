package org.codeflow.veterineryproject.staff.service;

import org.codeflow.veterineryproject.staff.dto.WorkingHoursDto.Interval;

import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;

/**
 * Değer nesnesi: bir hekimin bir şubedeki haftalık programı. İstek içindeki tutarlılığı (başlangıç &lt; bitiş, aynı gün
 * çakışan aralık yok) doğrular. Şubeler arası çakışmayı ise DB'deki EXCLUDE kısıtı garanti eder (V23).
 */
public final class WeeklySchedule {

    public record Slot(int dayOfWeek, LocalTime start, LocalTime end) {
        boolean overlaps(Slot other) {
            return dayOfWeek == other.dayOfWeek && start.isBefore(other.end) && other.start.isBefore(end);
        }
    }

    private final List<Slot> slots;

    private WeeklySchedule(List<Slot> slots) {
        this.slots = slots;
    }

    public static WeeklySchedule of(List<Interval> intervals) {
        List<Slot> slots = intervals.stream()
                .map(i -> new Slot(i.dayOfWeek(), LocalTime.parse(i.startTime()), LocalTime.parse(i.endTime())))
                .sorted(Comparator.comparingInt(Slot::dayOfWeek).thenComparing(Slot::start))
                .toList();
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            if (!slot.start().isBefore(slot.end())) {
                throw new IllegalArgumentException("Başlangıç saati bitiş saatinden önce olmalıdır.");
            }
            if (i > 0 && slots.get(i - 1).overlaps(slot)) {
                throw new IllegalArgumentException("Aynı gün için çalışma aralıkları çakışıyor.");
            }
        }
        return new WeeklySchedule(slots);
    }

    public List<Slot> slots() {
        return slots;
    }
}
