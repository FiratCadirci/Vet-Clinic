package org.codeflow.veterineryproject.appointment.controller;

import jakarta.validation.Valid;
import org.codeflow.veterineryproject.appointment.dto.AppointmentResponse;
import org.codeflow.veterineryproject.appointment.dto.CancelAppointmentRequest;
import org.codeflow.veterineryproject.appointment.dto.CreateAppointmentRequest;
import org.codeflow.veterineryproject.appointment.dto.RescheduleRequest;
import org.codeflow.veterineryproject.appointment.repository.AppointmentJdbcRepository.Filter;
import org.codeflow.veterineryproject.appointment.service.AppointmentService;
import org.codeflow.veterineryproject.auth.security.Permission;
import org.codeflow.veterineryproject.common.pagination.PageResponse;
import org.codeflow.veterineryproject.common.pagination.PaginationUtils;
import org.codeflow.veterineryproject.common.time.ApiDateTime;
import org.codeflow.veterineryproject.encounter.dto.CloseEncounterRequest;
import org.codeflow.veterineryproject.encounter.dto.EncounterResponse;
import org.codeflow.veterineryproject.encounter.dto.StartFromAppointmentRequest;
import org.codeflow.veterineryproject.encounter.service.EncounterService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/appointments")
@PreAuthorize("hasAuthority('" + Permission.APPOINTMENTS_MANAGE + "')")
public class AppointmentController {

    private final AppointmentService service;
    private final EncounterService encounters;

    public AppointmentController(AppointmentService service, EncounterService encounters) {
        this.service = service;
        this.encounters = encounters;
    }

    /** Date bounds are exclusive overlap limits; both canonical and legacy parameter names are accepted. */
    @GetMapping
    public PageResponse<AppointmentResponse> list(
            @RequestParam(required = false) String startDateTime,
            @RequestParam(required = false) String endDateTime,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) UUID clinicId,
            @RequestParam(required = false) UUID vetId,
            @RequestParam(required = false) UUID veterinarianId,
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) String appointmentType,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String bookingChannel,
            @RequestParam(required = false) UUID ownerId,
            @RequestParam(required = false) UUID clientId,
            @RequestParam(required = false) UUID animalId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort
    ) {
        String resolvedStart = firstNonNull(startDateTime, from);
        String resolvedEnd = firstNonNull(endDateTime, to);
        Filter filter = new Filter(resolvedStart == null ? null : ApiDateTime.parseOffset(resolvedStart),
                resolvedEnd == null ? null : ApiDateTime.parseOffset(resolvedEnd), clinicId,
                firstNonNull(vetId, veterinarianId), status, firstNonNull(appointmentType, type), bookingChannel,
                firstNonNull(ownerId, clientId), animalId);
        return service.list(filter, PaginationUtils.createPageable(page, size, parseSort(sort)));
    }

    @GetMapping("/{id}")
    public AppointmentResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AppointmentResponse create(@RequestHeader("X-Clinic-Id") String clinicId,
                                      @Valid @RequestBody CreateAppointmentRequest request) {
        return service.create(request);
    }

    @RequestMapping(path = "/{id}/confirm", method = {RequestMethod.POST, RequestMethod.PATCH})
    public AppointmentResponse confirm(@RequestHeader("X-Clinic-Id") String clinicId, @PathVariable UUID id) {
        return service.confirm(id);
    }

    @RequestMapping(path = "/{id}/check-in", method = {RequestMethod.POST, RequestMethod.PATCH})
    public AppointmentResponse checkIn(@RequestHeader("X-Clinic-Id") String clinicId, @PathVariable UUID id) {
        return service.checkIn(id);
    }

    @RequestMapping(path = "/{id}/no-show", method = {RequestMethod.POST, RequestMethod.PATCH})
    public AppointmentResponse noShow(@RequestHeader("X-Clinic-Id") String clinicId, @PathVariable UUID id) {
        return service.noShow(id);
    }

    @RequestMapping(path = "/{id}/cancel", method = {RequestMethod.POST, RequestMethod.PATCH})
    public AppointmentResponse cancel(@RequestHeader("X-Clinic-Id") String clinicId, @PathVariable UUID id,
                                      @Valid @RequestBody CancelAppointmentRequest request) {
        return service.cancel(id, request.cancellationReason());
    }

    @RequestMapping(path = "/{id}/reschedule", method = {RequestMethod.POST, RequestMethod.PATCH})
    public AppointmentResponse reschedule(@RequestHeader("X-Clinic-Id") String clinicId, @PathVariable UUID id,
                                          @Valid @RequestBody RescheduleRequest request) {
        return service.reschedule(id, request);
    }

    @PatchMapping("/{id}/start")
    @PreAuthorize("hasAuthority('" + Permission.APPOINTMENTS_MANAGE + "') and hasAuthority('" + Permission.CLINICAL_WRITE + "')")
    public EncounterResponse start(@RequestHeader("X-Clinic-Id") String clinicId, @PathVariable UUID id,
                                   @Valid @RequestBody(required = false) StartFromAppointmentRequest request) {
        return encounters.startFromAppointment(id, request);
    }

    @PatchMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('" + Permission.APPOINTMENTS_MANAGE + "') and hasAuthority('" + Permission.CLINICAL_WRITE + "')")
    public AppointmentResponse complete(@RequestHeader("X-Clinic-Id") String clinicId, @PathVariable UUID id,
                                        @Valid @RequestBody(required = false) CloseEncounterRequest request) {
        encounters.completeAppointment(id, request);
        return service.get(id);
    }

    private static String firstNonNull(String preferred, String legacy) {
        return preferred == null ? legacy : preferred;
    }

    private static UUID firstNonNull(UUID preferred, UUID legacy) {
        return preferred == null ? legacy : preferred;
    }

    private static Sort parseSort(String value) {
        if (value == null || value.isBlank()) {
            return Sort.by(Sort.Order.asc("startsAt"));
        }
        String[] parts = value.split(",", -1);
        if (parts.length > 2 || parts[0].isBlank()) {
            throw new IllegalArgumentException("sort biçimi field,asc|desc olmalıdır.");
        }
        String property = parts[0].trim();
        if (!List.of("startsAt", "endsAt", "appointmentNo", "status", "appointmentType", "bookingChannel")
                .contains(property)) {
            throw new IllegalArgumentException("Desteklenmeyen randevu sıralama alanı: " + property);
        }
        Sort.Direction direction = parts.length == 1 ? Sort.Direction.ASC : Sort.Direction.fromString(parts[1].trim());
        return Sort.by(new Sort.Order(direction, property));
    }
}
