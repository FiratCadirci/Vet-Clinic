package org.codeflow.veterineryproject.catalog.controller;

import jakarta.validation.Valid;
import org.codeflow.veterineryproject.auth.security.Permission;
import org.codeflow.veterineryproject.catalog.dto.ServiceCatalogDto.ServiceRequest;
import org.codeflow.veterineryproject.catalog.dto.ServiceCatalogDto.ServiceResponse;
import org.codeflow.veterineryproject.catalog.service.ServiceCatalogService;
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

/** Klinik hizmetleri kataloğu. Okuma oturum açmış tüm personele, yazma services.manage yetkisine açıktır. */
@RestController
@RequestMapping("/api/v1/services")
public class ServiceCatalogController {

    private static final String MANAGE = "hasAuthority('" + Permission.SERVICES_MANAGE + "')";

    private final ServiceCatalogService service;

    public ServiceCatalogController(ServiceCatalogService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<ServiceResponse> list(@RequestParam(defaultValue = "false") boolean includeInactive) {
        return service.list(includeInactive);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ServiceResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(MANAGE)
    public ServiceResponse create(@RequestHeader("X-Clinic-Id") String clinicId, @Valid @RequestBody ServiceRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize(MANAGE)
    public ServiceResponse update(@RequestHeader("X-Clinic-Id") String clinicId, @PathVariable UUID id,
                                  @Valid @RequestBody ServiceRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(MANAGE)
    public void delete(@RequestHeader("X-Clinic-Id") String clinicId, @PathVariable UUID id) {
        service.delete(id);
    }
}
