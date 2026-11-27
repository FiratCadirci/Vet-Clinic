package org.codeflow.veterineryproject.organization.controller;

import jakarta.validation.Valid;
import org.codeflow.veterineryproject.auth.security.Permission;
import org.codeflow.veterineryproject.organization.dto.OrganizationDto.ProfileResponse;
import org.codeflow.veterineryproject.organization.dto.OrganizationDto.UpdateProfileRequest;
import org.codeflow.veterineryproject.organization.service.OrganizationProfileService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Oturumdaki kliniğin (organizasyon) profili. Okuma tüm personele, güncelleme branches.manage yetkisine açıktır. */
@RestController
@RequestMapping("/api/v1/organization")
public class OrganizationProfileController {

    private final OrganizationProfileService service;

    public OrganizationProfileController(OrganizationProfileService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ProfileResponse get() {
        return service.get();
    }

    @PutMapping
    @PreAuthorize("hasAuthority('" + Permission.BRANCHES_MANAGE + "')")
    public ProfileResponse update(@RequestHeader("X-Clinic-Id") String clinicId,
                                  @Valid @RequestBody UpdateProfileRequest request) {
        return service.update(request);
    }
}
