package org.codeflow.veterineryproject.appointment;

import org.codeflow.veterineryproject.appointment.model.AppointmentStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AppointmentStatusTest {

    @Test
    void bookedCanCheckInButNotComplete() {
        assertThat(AppointmentStatus.BOOKED.canMoveTo(AppointmentStatus.CHECKED_IN)).isTrue();
        assertThat(AppointmentStatus.BOOKED.canMoveTo(AppointmentStatus.CONFIRMED)).isTrue();
        assertThat(AppointmentStatus.BOOKED.canMoveTo(AppointmentStatus.COMPLETED)).isFalse();
    }

    @Test
    void finalStatesHaveNoTransitions() {
        for (AppointmentStatus s : new AppointmentStatus[]{AppointmentStatus.CANCELLED, AppointmentStatus.NO_SHOW}) {
            assertThat(s.allowedNext()).isEmpty();
        }
    }

    @Test
    void checkedInOnlyCancellable() {
        assertThat(AppointmentStatus.CHECKED_IN.canMoveTo(AppointmentStatus.CANCELLED)).isTrue();
        assertThat(AppointmentStatus.CHECKED_IN.canMoveTo(AppointmentStatus.NO_SHOW)).isFalse();
    }
}
