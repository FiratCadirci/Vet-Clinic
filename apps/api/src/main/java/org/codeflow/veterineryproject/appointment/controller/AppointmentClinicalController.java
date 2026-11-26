package org.codeflow.veterineryproject.appointment.controller;

import org.codeflow.veterineryproject.appointment.dto.AppointmentClinicalSummaryResponse;
import org.codeflow.veterineryproject.appointment.service.AppointmentClinicalService;
import org.codeflow.veterineryproject.auth.security.Permission;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/appointments")
public class AppointmentClinicalController {

    private final AppointmentClinicalService service;

    public AppointmentClinicalController(AppointmentClinicalService service) {
        this.service = service;
    }

    @GetMapping("/{appointmentId}/clinical-summary")
    @PreAuthorize("hasAuthority('" + Permission.CLINICAL_READ + "')")
    public AppointmentClinicalSummaryResponse clinicalSummary(@PathVariable UUID appointmentId) {
        return service.summary(appointmentId);
    }
}
