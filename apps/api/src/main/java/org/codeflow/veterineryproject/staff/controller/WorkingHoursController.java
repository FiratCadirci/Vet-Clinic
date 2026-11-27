package org.codeflow.veterineryproject.staff.controller;

import jakarta.validation.Valid;
import org.codeflow.veterineryproject.auth.security.Permission;
import org.codeflow.veterineryproject.staff.dto.WorkingHoursDto.ReplaceRequest;
import org.codeflow.veterineryproject.staff.dto.WorkingHoursDto.VeterinarianOption;
import org.codeflow.veterineryproject.staff.dto.WorkingHoursDto.WorkingHourResponse;
import org.codeflow.veterineryproject.staff.service.WorkingHoursService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Hekim çalışma saatleri. Okuma randevu verenlere de açıktır (takvim/uygunluk), değiştirme schedule.manage ister.
 * PUT'ta hedef şube X-Clinic-Id başlığıdır.
 */
@RestController
@RequestMapping("/api/v1/working-hours")
public class WorkingHoursController {

    private static final String READ = "hasAnyAuthority('" + Permission.SCHEDULE_MANAGE + "', '" + Permission.APPOINTMENTS_MANAGE + "')";

    private final WorkingHoursService service;

    public WorkingHoursController(WorkingHoursService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(READ)
    public List<WorkingHourResponse> list(@RequestParam(required = false) UUID clinicId,
                                          @RequestParam(required = false) UUID veterinarianId) {
        return service.list(clinicId, veterinarianId);
    }

    @GetMapping("/veterinarians")
    @PreAuthorize(READ)
    public List<VeterinarianOption> veterinarians(@RequestParam(required = false) UUID clinicId) {
        return service.veterinarians(clinicId);
    }

    @PutMapping
    @PreAuthorize("hasAuthority('" + Permission.SCHEDULE_MANAGE + "')")
    public List<WorkingHourResponse> replace(@RequestHeader("X-Clinic-Id") String clinicId,
                                             @Valid @RequestBody ReplaceRequest request) {
        return service.replace(request);
    }
}
