package org.codeflow.veterineryproject.appointment.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AppointmentResponse(
        UUID id,
        String appointmentNo,
        UUID clinicId,
        UUID clientId,
        String clientName,
        UUID animalId,
        String animalName,
        String patientNo,
        UUID veterinarianId,
        String veterinarianName,
        String appointmentType,
        String status,
        @JsonSerialize(using = AppointmentDateTimeSerializer.class)
        OffsetDateTime startsAt,
        @JsonSerialize(using = AppointmentDateTimeSerializer.class)
        OffsetDateTime endsAt,
        String reason,
        String internalNotes,
        String bookingChannel,
        @JsonSerialize(using = AppointmentDateTimeSerializer.class)
        OffsetDateTime cancelledAt,
        String cancellationReason,
        @JsonSerialize(using = AppointmentDateTimeSerializer.class)
        OffsetDateTime createdAt,
        @JsonSerialize(using = AppointmentDateTimeSerializer.class)
        OffsetDateTime updatedAt
) {
}
