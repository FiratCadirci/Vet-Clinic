package org.codeflow.veterineryproject.encounter.controller;

import jakarta.validation.Valid;
import org.codeflow.veterineryproject.auth.security.Permission;
import org.codeflow.veterineryproject.common.pagination.PageResponse;
import org.codeflow.veterineryproject.common.pagination.PaginationUtils;
import org.codeflow.veterineryproject.encounter.dto.AddendumRequest;
import org.codeflow.veterineryproject.encounter.dto.AddendumResponse;
import org.codeflow.veterineryproject.encounter.dto.CloseEncounterRequest;
import org.codeflow.veterineryproject.encounter.dto.CreateEncounterRequest;
import org.codeflow.veterineryproject.encounter.dto.DiagnosisRequest;
import org.codeflow.veterineryproject.encounter.dto.EncounterFlowResponse;
import org.codeflow.veterineryproject.encounter.dto.EncounterResponse;
import org.codeflow.veterineryproject.encounter.dto.EncounterStatus;
import org.codeflow.veterineryproject.encounter.dto.ExaminationRequest;
import org.codeflow.veterineryproject.encounter.dto.HistoryItemResponse;
import org.codeflow.veterineryproject.encounter.dto.PlanRequest;
import org.codeflow.veterineryproject.encounter.dto.StartFromAppointmentRequest;
import org.codeflow.veterineryproject.encounter.dto.TreatmentRequest;
import org.codeflow.veterineryproject.encounter.dto.TreatmentResponse;
import org.codeflow.veterineryproject.encounter.dto.UpdateEncounterRequest;
import org.codeflow.veterineryproject.encounter.service.EncounterService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Yazma uçlarında X-Clinic-Id zorunludur: kayıt o şubeye ait olur ve yalnızca o şubeden değiştirilebilir.
 * Okuma, klinik grubu içindeki tüm şubelerin kayıtlarını kapsar (RLS).
 */
@RestController
@RequestMapping("/api/v1")
public class EncounterController {

    private static final String READ = "hasAuthority('" + Permission.CLINICAL_READ + "')";
    private static final String WRITE = "hasAuthority('" + Permission.CLINICAL_WRITE + "')";
    private static final String DELETE = "hasAuthority('" + Permission.CLINICAL_DELETE + "')";

    private final EncounterService service;

    public EncounterController(EncounterService service) {
        this.service = service;
    }

    @PostMapping("/encounters")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(WRITE)
    public EncounterResponse startWalkIn(@RequestHeader("X-Clinic-Id") String clinicId, @Valid @RequestBody CreateEncounterRequest request) {
        return service.startWalkIn(request);
    }

    @PostMapping("/appointments/{appointmentId}/encounter")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(WRITE)
    public EncounterResponse startFromAppointment(
            @RequestHeader("X-Clinic-Id") String clinicId,
            @PathVariable UUID appointmentId,
            @Valid @RequestBody(required = false) StartFromAppointmentRequest request
    ) {
        return service.startFromAppointment(appointmentId, request);
    }

    @GetMapping("/encounters")
    @PreAuthorize(READ)
    public PageResponse<EncounterResponse> list(
            @RequestParam(required = false) UUID animalId,
            @RequestParam(required = false) EncounterStatus status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return service.list(animalId, status, PaginationUtils.createPageable(page, size));
    }

    /** Muayene türlerine göre akış tanımları (Template Method kancalarının istemciye açılan özeti). */
    @GetMapping("/encounter-flows")
    @PreAuthorize(READ)
    public List<EncounterFlowResponse> flows() {
        return service.flowDefinitions();
    }

    @GetMapping("/encounters/{id}")
    @PreAuthorize(READ)
    public EncounterResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PatchMapping("/encounters/{id}")
    @PreAuthorize(WRITE)
    public EncounterResponse update(@RequestHeader("X-Clinic-Id") String clinicId, @PathVariable UUID id,
                                    @Valid @RequestBody UpdateEncounterRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/encounters/{id}/examination")
    @PreAuthorize(WRITE)
    public EncounterResponse examination(@RequestHeader("X-Clinic-Id") String clinicId, @PathVariable UUID id,
                                         @Valid @RequestBody ExaminationRequest request) {
        return service.examination(id, request);
    }

    @PostMapping("/encounters/{id}/diagnosis")
    @PreAuthorize(WRITE)
    public EncounterResponse diagnosis(@RequestHeader("X-Clinic-Id") String clinicId, @PathVariable UUID id,
                                       @Valid @RequestBody DiagnosisRequest request) {
        return service.diagnosis(id, request);
    }

    @PostMapping("/encounters/{id}/plan")
    @PreAuthorize(WRITE)
    public EncounterResponse plan(@RequestHeader("X-Clinic-Id") String clinicId, @PathVariable UUID id,
                                  @Valid @RequestBody PlanRequest request) {
        return service.plan(id, request);
    }

    @PostMapping("/encounters/{id}/close")
    @PreAuthorize(WRITE)
    public EncounterResponse close(@RequestHeader("X-Clinic-Id") String clinicId, @PathVariable UUID id,
                                   @Valid @RequestBody(required = false) CloseEncounterRequest request) {
        return service.close(id, request);
    }

    @PostMapping("/encounters/{id}/addenda")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(WRITE)
    public AddendumResponse addAddendum(@RequestHeader("X-Clinic-Id") String clinicId, @PathVariable UUID id,
                                        @Valid @RequestBody AddendumRequest request) {
        return service.addAddendum(id, request);
    }

    @DeleteMapping("/encounters/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(DELETE)
    public void delete(@RequestHeader("X-Clinic-Id") String clinicId, @PathVariable UUID id) {
        service.delete(id);
    }

    @PostMapping("/encounters/{id}/treatments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(WRITE)
    public TreatmentResponse addTreatment(@RequestHeader("X-Clinic-Id") String clinicId, @PathVariable UUID id,
                                          @Valid @RequestBody TreatmentRequest request) {
        return service.addTreatment(id, request);
    }

    @GetMapping("/encounters/{id}/treatments")
    @PreAuthorize(READ)
    public List<TreatmentResponse> listTreatments(@PathVariable UUID id) {
        return service.listTreatments(id);
    }

    @DeleteMapping("/encounters/{id}/treatments/{treatmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(DELETE)
    public void deleteTreatment(@RequestHeader("X-Clinic-Id") String clinicId, @PathVariable UUID id, @PathVariable UUID treatmentId) {
        service.deleteTreatment(id, treatmentId);
    }

    @GetMapping("/animals/{animalId}/history")
    @PreAuthorize(READ)
    public PageResponse<HistoryItemResponse> history(
            @PathVariable UUID animalId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return service.history(animalId, PaginationUtils.createPageable(page, size));
    }
}
