package org.codeflow.veterineryproject.vaccine.controller;

import jakarta.validation.Valid;
import org.codeflow.veterineryproject.auth.security.Permission;
import org.codeflow.veterineryproject.common.pagination.PageResponse;
import org.codeflow.veterineryproject.common.pagination.PaginationUtils;
import org.codeflow.veterineryproject.vaccine.dto.AnimalVaccineScheduleResponse;
import org.codeflow.veterineryproject.vaccine.dto.CreateProtocolRequest;
import org.codeflow.veterineryproject.vaccine.dto.GenerateProtocolsRequest;
import org.codeflow.veterineryproject.vaccine.dto.UpdateProtocolRequest;
import org.codeflow.veterineryproject.vaccine.dto.DueVaccineResponse;
import org.codeflow.veterineryproject.vaccine.dto.ProtocolResponse;
import org.codeflow.veterineryproject.vaccine.dto.RecordVaccineRequest;
import org.codeflow.veterineryproject.vaccine.dto.VaccineRecordResponse;
import org.codeflow.veterineryproject.vaccine.dto.VaccineScheduleTemplate;
import org.codeflow.veterineryproject.vaccine.service.VaccinationService;
import org.codeflow.veterineryproject.vaccine.service.VaccineProtocolService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class VaccineController {

    private static final String READ = "hasAuthority('" + Permission.CLINICAL_READ + "')";
    private static final String WRITE = "hasAuthority('" + Permission.CLINICAL_WRITE + "')";
    private static final String DELETE = "hasAuthority('" + Permission.CLINICAL_DELETE + "')";
    private static final String MANAGE = "hasAuthority('" + Permission.VACCINE_PROTOCOLS_MANAGE + "')";

    private final VaccineProtocolService protocols;
    private final VaccinationService vaccinations;

    public VaccineController(VaccineProtocolService protocols, VaccinationService vaccinations) {
        this.protocols = protocols;
        this.vaccinations = vaccinations;
    }

    @PostMapping("/vaccine-protocols")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(MANAGE)
    public ProtocolResponse createProtocol(@Valid @RequestBody CreateProtocolRequest request) {
        return protocols.create(request);
    }

    /** FG-D1: türün fabrika taslağı (onaysız protokoller). */
    @PostMapping("/vaccine-protocols/generate")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(MANAGE)
    public List<ProtocolResponse> generate(@Valid @RequestBody GenerateProtocolsRequest request) {
        return protocols.generate(request);
    }

    /** FG-D2: kalem değişikliği yeni taslak sürüm oluşturur ve onayı sıfırlar. */
    @PutMapping("/vaccine-protocols/{id}")
    @PreAuthorize(MANAGE)
    public ProtocolResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateProtocolRequest request) {
        return protocols.update(id, request);
    }

    @DeleteMapping("/vaccine-protocols/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(MANAGE)
    public void deleteDraft(@PathVariable UUID id) {
        protocols.deleteDraft(id);
    }

    @GetMapping("/vaccine-protocols")
    @PreAuthorize(READ)
    public PageResponse<ProtocolResponse> listProtocols(
            @RequestParam(required = false) UUID speciesId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return protocols.list(speciesId, status, PaginationUtils.createPageable(page, size));
    }

    @GetMapping("/vaccine-protocols/{id}")
    @PreAuthorize(READ)
    public ProtocolResponse getProtocol(@PathVariable UUID id) {
        return protocols.get(id);
    }

    @PostMapping("/vaccine-protocols/{id}/approve")
    @PreAuthorize(MANAGE)
    public ProtocolResponse approve(@RequestHeader("X-Clinic-Id") String clinicId, @PathVariable UUID id) {
        return protocols.approve(id);
    }

    @PostMapping("/vaccine-protocols/{id}/retire")
    @PreAuthorize(MANAGE)
    public ProtocolResponse retire(@PathVariable UUID id) {
        return protocols.retire(id);
    }

    @GetMapping("/animals/{animalId}/vaccine-template")
    @PreAuthorize(READ)
    public VaccineScheduleTemplate template(@PathVariable UUID animalId) {
        return protocols.templateFor(animalId);
    }

    /** FG-D4: hastanın aşı takvimi (planlanan / uygulanan / geciken dozlar ve en yakın vade). */
    @GetMapping("/animals/{animalId}/vaccine-schedule")
    @PreAuthorize(READ)
    public AnimalVaccineScheduleResponse schedule(@PathVariable UUID animalId) {
        return protocols.scheduleFor(animalId);
    }

    @PostMapping("/vaccinations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(WRITE)
    public VaccineRecordResponse record(@RequestHeader("X-Clinic-Id") String clinicId, @Valid @RequestBody RecordVaccineRequest request) {
        return vaccinations.record(request);
    }

    @DeleteMapping("/vaccinations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(DELETE)
    public void delete(@RequestHeader("X-Clinic-Id") String clinicId, @PathVariable UUID id) {
        vaccinations.delete(id);
    }

    @GetMapping("/animals/{animalId}/vaccinations")
    @PreAuthorize(READ)
    public PageResponse<VaccineRecordResponse> listByAnimal(
            @PathVariable UUID animalId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return vaccinations.listByAnimal(animalId, PaginationUtils.createPageable(page, size));
    }

    @GetMapping("/animals/{animalId}/vaccinations/due")
    @PreAuthorize(READ)
    public PageResponse<DueVaccineResponse> dueByAnimal(
            @PathVariable UUID animalId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer days,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return vaccinations.due(animalId, status, days, PaginationUtils.createPageable(page, size));
    }

    @GetMapping("/vaccinations/due")
    @PreAuthorize(READ)
    public PageResponse<DueVaccineResponse> due(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer days,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return vaccinations.due(null, status, days, PaginationUtils.createPageable(page, size));
    }
}
