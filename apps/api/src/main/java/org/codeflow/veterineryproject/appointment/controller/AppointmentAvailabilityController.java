package org.codeflow.veterineryproject.appointment.controller;

import org.codeflow.veterineryproject.appointment.dto.AvailabilityResponse;
import org.codeflow.veterineryproject.appointment.dto.BookingOptionsResponse;
import org.codeflow.veterineryproject.appointment.service.AvailabilityService;
import org.codeflow.veterineryproject.auth.security.Permission;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/** Randevu formunun okuma uçları: uygun saatler ve form seçenekleri (hizmet + hekim). */
@RestController
@RequestMapping("/api/v1/appointments")
@PreAuthorize("hasAuthority('" + Permission.APPOINTMENTS_MANAGE + "')")
public class AppointmentAvailabilityController {

    private final AvailabilityService service;

    public AppointmentAvailabilityController(AvailabilityService service) {
        this.service = service;
    }

    /** {@code date}: klinik yerel günü (yyyy-MM-dd). {@code vetId}, {@code veterinarianId} için kısa addır. */
    @GetMapping("/availability")
    public AvailabilityResponse availability(
            @RequestParam(required = false) UUID clinicId,
            @RequestParam(required = false) UUID serviceId,
            @RequestParam(required = false) UUID veterinarianId,
            @RequestParam(required = false) UUID vetId,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) UUID excludeAppointmentId
    ) {
        return service.availability(clinicId, serviceId, veterinarianId == null ? vetId : veterinarianId,
                date == null || date.isBlank() ? null : LocalDate.parse(date.trim()), excludeAppointmentId);
    }

    @GetMapping("/booking-options")
    public BookingOptionsResponse bookingOptions(@RequestParam(required = false) UUID clinicId) {
        return service.bookingOptions(clinicId);
    }
}
