package org.codeflow.veterineryproject.appointment.model;

import java.time.LocalTime;
import java.util.Objects;

/** Hekimin bir gündeki çalışma penceresi (klinik yerel saati). */
public record WorkingWindow(LocalTime startsAt, LocalTime endsAt) {
    public WorkingWindow {
        Objects.requireNonNull(startsAt);
        Objects.requireNonNull(endsAt);
    }
}
