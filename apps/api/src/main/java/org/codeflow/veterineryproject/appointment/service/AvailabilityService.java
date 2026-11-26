package org.codeflow.veterineryproject.appointment.service;

import jakarta.persistence.EntityNotFoundException;
import org.codeflow.veterineryproject.appointment.dto.AvailabilityResponse;
import org.codeflow.veterineryproject.appointment.dto.AvailabilityResponse.SlotResponse;
import org.codeflow.veterineryproject.appointment.dto.BookingOptionsResponse;
import org.codeflow.veterineryproject.appointment.model.BusyInterval;
import org.codeflow.veterineryproject.appointment.model.WorkingWindow;
import org.codeflow.veterineryproject.appointment.repository.AvailabilityJdbcRepository;
import org.codeflow.veterineryproject.appointment.repository.AvailabilityJdbcRepository.ServiceRow;
import org.codeflow.veterineryproject.common.api.BusinessRuleException;
import org.codeflow.veterineryproject.common.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Uygun saat sorgusu (FG-E2): verileri RLS altında okur, hesabı {@link SlotCalculator}'a bırakır.
 * Bu liste yalnızca kullanıcıya yol gösterir; aynı saati eşzamanlı alan iki istekten birini DB'deki EXCLUDE kısıtı reddeder (FG-E3).
 */
@Service
public class AvailabilityService {

    private final AvailabilityJdbcRepository repository;
    private final SlotCalculator calculator = new SlotCalculator();
    private final Clock clock = Clock.systemUTC();

    public AvailabilityService(AvailabilityJdbcRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public AvailabilityResponse availability(UUID clinicId, UUID serviceId, UUID veterinarianId, LocalDate date,
                                             UUID excludeAppointmentId) {
        UUID clinic = resolveClinic(clinicId);
        if ((serviceId == null && excludeAppointmentId == null) || veterinarianId == null || date == null) {
            throw new IllegalArgumentException("serviceId (veya ertelemede excludeAppointmentId), veterinarianId ve date zorunludur.");
        }
        ZoneId zone = repository.clinicTimezone(clinic).map(ZoneId::of)
                .orElseThrow(() -> new EntityNotFoundException("Şube bulunamadı."));
        // Ertelemede hizmet seçilmez: süre, ertelenen randevunun kendi süresidir.
        int minutes = serviceId != null ? serviceMinutes(serviceId) : repository.appointmentMinutes(excludeAppointmentId)
                .orElseThrow(() -> new EntityNotFoundException("Randevu bulunamadı."));
        if (!repository.veterinarianActive(veterinarianId)) {
            throw new EntityNotFoundException("Veteriner hekim bulunamadı veya pasif.");
        }

        Duration duration = Duration.ofMinutes(minutes);
        int dayOfWeek = date.getDayOfWeek().getValue() % 7; // PostgreSQL DOW: 0 = Pazar
        List<WorkingWindow> windows = repository.workingWindows(clinic, veterinarianId, date, dayOfWeek);
        List<BusyInterval> busy = windows.isEmpty() ? List.of() : repository.busyIntervals(veterinarianId,
                date.atStartOfDay(zone).toOffsetDateTime(), date.plusDays(1).atStartOfDay(zone).toOffsetDateTime(),
                excludeAppointmentId);
        List<SlotResponse> slots = calculator.calculate(date, zone, windows, duration, busy, clock.instant()).stream()
                .map(s -> new SlotResponse(s.startsAt(), s.endsAt(), s.time()))
                .toList();
        return new AvailabilityResponse(date, clinic, veterinarianId, serviceId, minutes, slots);
    }

    private int serviceMinutes(UUID serviceId) {
        ServiceRow service = repository.activeService(serviceId)
                .orElseThrow(() -> new EntityNotFoundException("Hizmet bulunamadı."));
        if (service.durationMinutes() == null) {
            throw new BusinessRuleException("Hizmet için süre tanımlı değil.");
        }
        return service.durationMinutes();
    }

    @Transactional(readOnly = true)
    public BookingOptionsResponse bookingOptions(UUID clinicId) {
        UUID clinic = resolveClinic(clinicId);
        if (repository.clinicTimezone(clinic).isEmpty()) {
            throw new EntityNotFoundException("Şube bulunamadı.");
        }
        return new BookingOptionsResponse(repository.activeServices(), repository.veterinariansForClinic(clinic));
    }

    /** Sorgudaki şube verilmezse oturumdaki şube (X-Clinic-Id) kullanılır. */
    private static UUID resolveClinic(UUID clinicId) {
        if (clinicId != null) {
            return clinicId;
        }
        UUID current = TenantContext.require().clinicId();
        if (current == null) {
            throw new IllegalArgumentException("clinicId veya X-Clinic-Id gereklidir.");
        }
        return current;
    }
}
