package org.codeflow.veterineryproject.appointment.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelAppointmentRequest(@JsonAlias("reason") @NotBlank @Size(max = 500) String cancellationReason) {
}
