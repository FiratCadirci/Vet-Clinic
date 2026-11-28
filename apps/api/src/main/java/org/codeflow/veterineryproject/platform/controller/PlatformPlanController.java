package org.codeflow.veterineryproject.platform.controller;

import jakarta.validation.Valid;
import org.codeflow.veterineryproject.auth.security.PlatformAdminPrincipal;
import org.codeflow.veterineryproject.platform.dto.PlatformDto.CreatePlanRequest;
import org.codeflow.veterineryproject.platform.dto.PlatformDto.PlanResponse;
import org.codeflow.veterineryproject.platform.dto.PlatformDto.UpdatePlanRequest;
import org.codeflow.veterineryproject.platform.service.PlatformPlanService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Platform yöneticisi: abonelik paketleri (aylık ve yıllık satırlar ayrı). */
@RestController
@RequestMapping("/api/platform/plans")
@PreAuthorize("hasAuthority('" + PlatformAdminPrincipal.AUTHORITY + "')")
public class PlatformPlanController {

    private final PlatformPlanService service;

    public PlatformPlanController(PlatformPlanService service) {
        this.service = service;
    }

    @GetMapping
    public List<PlanResponse> list() {
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PlanResponse create(@Valid @RequestBody CreatePlanRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public PlanResponse update(@PathVariable UUID id, @Valid @RequestBody UpdatePlanRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
