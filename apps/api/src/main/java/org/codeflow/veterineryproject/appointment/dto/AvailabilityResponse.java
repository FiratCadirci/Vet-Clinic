package org.codeflow.veterineryproject.appointment.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Seçilen şube + hizmet + hekim + gün için boş başlangıç saatleri (FG-E2). Saatler Europe/Istanbul offset'iyle döner. */
public record AvailabilityResponse(
        LocalDate date,
        UUID clinicId,
        UUID veterinarianId,
        UUID serviceId,
        int durationMinutes,
        List<SlotResponse> slots
) {
    public record SlotResponse(
            @JsonSerialize(using = AppointmentDateTimeSerializer.class) OffsetDateTime startsAt,
            @JsonSerialize(using = AppointmentDateTimeSerializer.class) OffsetDateTime endsAt,
            String time
    ) {
    }
}
