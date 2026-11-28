package org.codeflow.veterineryproject.report.model;

import java.util.UUID;

/** Bir hekimin bir şubede aralıktaki dolu (iptal/gelmedi dışındaki) randevu dakikaları. */
public record BookedMinutes(UUID clinicId, UUID veterinarianId, long minutes) {
}
