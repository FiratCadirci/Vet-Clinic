package org.codeflow.veterineryproject.appointment.model;

import java.time.OffsetDateTime;

/** Boş randevu başlangıcı; {@code time} klinik yerel saatidir ("09:30"). */
public record Slot(OffsetDateTime startsAt, OffsetDateTime endsAt, String time) {
}
