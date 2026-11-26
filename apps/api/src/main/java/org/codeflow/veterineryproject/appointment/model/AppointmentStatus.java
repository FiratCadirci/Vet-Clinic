package org.codeflow.veterineryproject.appointment.model;

import java.util.EnumSet;
import java.util.Set;

/** vet.appointments.status değerleri; IN_PROGRESS ve COMPLETED yalnızca muayene akışıyla değişir. */
public enum AppointmentStatus {
    REQUESTED, BOOKED, CONFIRMED, CHECKED_IN, IN_PROGRESS, COMPLETED, CANCELLED, NO_SHOW;

    public Set<AppointmentStatus> allowedNext() {
        return switch (this) {
            case REQUESTED -> EnumSet.of(BOOKED, CONFIRMED, CANCELLED);
            case BOOKED -> EnumSet.of(CONFIRMED, CHECKED_IN, CANCELLED, NO_SHOW);
            case CONFIRMED -> EnumSet.of(CHECKED_IN, CANCELLED, NO_SHOW);
            case CHECKED_IN -> EnumSet.of(CANCELLED);
            default -> EnumSet.noneOf(AppointmentStatus.class);
        };
    }

    public boolean canMoveTo(AppointmentStatus target) {
        return allowedNext().contains(target);
    }
}
