package org.codeflow.veterineryproject.report.service;

import org.codeflow.veterineryproject.report.dto.ReportDto.OccupancyRow;
import org.codeflow.veterineryproject.report.model.BookedMinutes;
import org.codeflow.veterineryproject.report.model.WorkingHoursSlot;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Randevu doluluk oranı (FG-L1). Veritabanından bağımsızdır; girdileri repository verir, birim testle doğrulanır.
 * Müsait dakika: aralıktaki her yerel gün için o gün geçerli çalışma aralıklarının süresi toplanır.
 * Çalışma saati tanımlı olmayan hekim/şube çiftinin randevuları kapasitesiz olduğu için sayılmaz.
 */
@Component
public class OccupancyCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /** [firstDay, endDayExclusive) günleri için hekim + şube bazında doluluk satırları. */
    public List<OccupancyRow> compute(Collection<WorkingHoursSlot> hours, Collection<BookedMinutes> booked,
                                      LocalDate firstDay, LocalDate endDayExclusive) {
        Map<Key, Acc> rows = new LinkedHashMap<>();
        for (WorkingHoursSlot slot : hours) {
            Acc acc = rows.computeIfAbsent(new Key(slot.clinicId(), slot.veterinarianId()), k -> new Acc(slot.vetName()));
            for (LocalDate day = firstDay; day.isBefore(endDayExclusive); day = day.plusDays(1)) {
                if (slot.appliesOn(day)) {
                    acc.available += slot.minutes();
                }
            }
        }
        for (BookedMinutes b : booked) {
            Acc acc = rows.get(new Key(b.clinicId(), b.veterinarianId()));
            if (acc != null) {
                acc.booked += b.minutes();
            }
        }
        List<OccupancyRow> result = new ArrayList<>(rows.size());
        rows.forEach((k, a) -> result.add(new OccupancyRow(k.clinicId(), k.veterinarianId(), a.vetName, a.available,
                a.booked, rate(a.booked, a.available))));
        return result;
    }

    /** Yüzde, bir ondalık basamak (yarım yukarı); müsait süre yoksa 0. */
    public BigDecimal rate(long bookedMinutes, long availableMinutes) {
        if (availableMinutes <= 0) {
            return BigDecimal.ZERO.setScale(1);
        }
        return BigDecimal.valueOf(bookedMinutes).multiply(HUNDRED)
                .divide(BigDecimal.valueOf(availableMinutes), 1, RoundingMode.HALF_UP);
    }

    /** Birden fazla satırın toplam doluluğu (ör. şube ya da genel özet). */
    public BigDecimal totalRate(Collection<OccupancyRow> rows) {
        long booked = rows.stream().mapToLong(OccupancyRow::bookedMinutes).sum();
        long available = rows.stream().mapToLong(OccupancyRow::availableMinutes).sum();
        return rate(booked, available);
    }

    private record Key(UUID clinicId, UUID veterinarianId) {
    }

    private static final class Acc {
        private final String vetName;
        private long available;
        private long booked;

        private Acc(String vetName) {
            this.vetName = vetName;
        }
    }
}
