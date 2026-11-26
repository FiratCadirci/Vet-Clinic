package org.codeflow.veterineryproject.appointment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CreateAppointmentRequest(
        @NotNull UUID animalId,
        UUID clientId,
        UUID veterinarianId,
        @Pattern(regexp = "EXAM|VACCINATION|FOLLOW_UP|SURGERY|GROOMING|LAB|IMAGING|OTHER") String appointmentType,
        @NotNull OffsetDateTime startsAt,
        @NotNull OffsetDateTime endsAt,
        @Size(max = 2000) String reason,
        @Size(max = 2000) String internalNotes,
        @Pattern(regexp = "STAFF|PHONE|WEB|MOBILE|WHATSAPP|OTHER") String bookingChannel
) {
}
