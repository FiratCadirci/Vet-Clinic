package org.codeflow.veterineryproject.report.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Hekimin bir şubedeki haftalık çalışma aralığı (vet.veterinarian_working_hours satırı).
 * dayOfWeek PostgreSQL DOW standardıdır: 0 = Pazar … 6 = Cumartesi. effectiveTo boşsa süresizdir.
 */
public record WorkingHoursSlot(UUID clinicId, UUID veterinarianId, String vetName, int dayOfWeek, LocalTime startsAt,
                               LocalTime endsAt, LocalDate effectiveFrom, LocalDate effectiveTo) {

    /** Verilen gün bu aralığın geçerli olduğu bir gün mü (hafta günü + geçerlilik tarihleri)? */
    public boolean appliesOn(LocalDate day) {
        int dow = day.getDayOfWeek().getValue() % 7;
        return dow == dayOfWeek
                && (effectiveFrom == null || !day.isBefore(effectiveFrom))
                && (effectiveTo == null || !day.isAfter(effectiveTo));
    }

    public long minutes() {
        return java.time.Duration.between(startsAt, endsAt).toMinutes();
    }
}
