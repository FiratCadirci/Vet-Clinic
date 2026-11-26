package org.codeflow.veterineryproject.appointment.model;

import java.time.OffsetDateTime;

/** Hekimi meşgul eden aralık (iptal/gelmedi dışındaki randevular), [başlangıç, bitiş) yarı açık. */
public record BusyInterval(OffsetDateTime startsAt, OffsetDateTime endsAt) {
}
