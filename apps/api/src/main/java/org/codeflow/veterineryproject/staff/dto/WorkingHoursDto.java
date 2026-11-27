package org.codeflow.veterineryproject.staff.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Hekim haftalık çalışma saatleri (vet.veterinarian_working_hours). Gün numarası DB ile aynıdır:
 * 0 = Pazar … 6 = Cumartesi (PostgreSQL EXTRACT(DOW)). Saatler "SS:DD" biçimindedir.
 */
public final class WorkingHoursDto {

    private static final String HH_MM = "^([01]\\d|2[0-3]):[0-5]\\d$";

    private WorkingHoursDto() {
    }

    public record Interval(
            @NotNull @Min(0) @Max(6) Integer dayOfWeek,
            @NotNull @Pattern(regexp = HH_MM, message = "SS:DD biçiminde olmalı") String startTime,
            @NotNull @Pattern(regexp = HH_MM, message = "SS:DD biçiminde olmalı") String endTime
    ) {
    }

    /** Hekimin X-Clinic-Id şubesindeki haftalık programını tümden değiştirir (boş liste = bu şubede çalışmıyor). */
    public record ReplaceRequest(
            @NotNull UUID veterinarianId,
            @NotNull @Size(max = 50) List<@Valid @NotNull Interval> hours
    ) {
    }

    public record WorkingHourResponse(UUID id, UUID clinicId, String clinicName, UUID veterinarianId, UUID userId,
                                      String veterinarianName, int dayOfWeek, String startTime, String endTime) {
    }

    /** Şubede görevli, hekim profili olan personel (çalışma saati tanımlanabilecek hekimler). */
    public record VeterinarianOption(UUID veterinarianId, UUID userId, String displayName, String licenseNo) {
    }
}
