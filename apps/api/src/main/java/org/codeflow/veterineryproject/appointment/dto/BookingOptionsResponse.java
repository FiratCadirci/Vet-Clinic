package org.codeflow.veterineryproject.appointment.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Randevu formunun seçenekleri: aktif hizmetler (süreleriyle) ve şubede çalışan aktif hekimler. */
public record BookingOptionsResponse(List<ServiceOption> services, List<VeterinarianOption> veterinarians) {

    public record ServiceOption(UUID id, String code, String name, Integer durationMinutes, BigDecimal price) {
    }

    public record VeterinarianOption(UUID id, String fullName) {
    }
}
