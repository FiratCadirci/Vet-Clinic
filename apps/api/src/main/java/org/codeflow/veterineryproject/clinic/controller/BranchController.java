package org.codeflow.veterineryproject.clinic.controller;

import jakarta.validation.Valid;
import org.codeflow.veterineryproject.auth.security.Permission;
import org.codeflow.veterineryproject.clinic.dto.BranchDto.BranchRequest;
import org.codeflow.veterineryproject.clinic.dto.BranchDto.BranchResponse;
import org.codeflow.veterineryproject.clinic.service.BranchService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Şube yönetimi (listeleme ClinicController'da). Yeni şube X-Clinic-Id şubesinin klinik grubunda açılır. */
@RestController
@RequestMapping("/api/v1/clinics")
@PreAuthorize("hasAuthority('" + Permission.BRANCHES_MANAGE + "')")
public class BranchController {

    private final BranchService service;

    public BranchController(BranchService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BranchResponse create(@RequestHeader("X-Clinic-Id") String clinicId, @Valid @RequestBody BranchRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public BranchResponse update(@RequestHeader("X-Clinic-Id") String clinicId, @PathVariable UUID id,
                                 @Valid @RequestBody BranchRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/deactivate")
    public BranchResponse deactivate(@RequestHeader("X-Clinic-Id") String clinicId, @PathVariable UUID id) {
        return service.deactivate(id);
    }

    @PostMapping("/{id}/activate")
    public BranchResponse activate(@RequestHeader("X-Clinic-Id") String clinicId, @PathVariable UUID id) {
        return service.activate(id);
    }
}
