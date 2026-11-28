package org.codeflow.veterineryproject.platform.controller;

import jakarta.validation.Valid;
import org.codeflow.veterineryproject.auth.security.PlatformAdminPrincipal;
import org.codeflow.veterineryproject.platform.dto.PlatformDto.CommissionRuleResponse;
import org.codeflow.veterineryproject.platform.dto.PlatformDto.CreateCommissionRuleRequest;
import org.codeflow.veterineryproject.platform.dto.PlatformDto.UpdateCommissionRuleRequest;
import org.codeflow.veterineryproject.platform.service.PlatformCommissionRuleService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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

/** Platform yöneticisi: komisyon kuralları (klinikler değiştiremez). */
@RestController
@RequestMapping("/api/platform/commission-rules")
@PreAuthorize("hasAuthority('" + PlatformAdminPrincipal.AUTHORITY + "')")
public class PlatformCommissionRuleController {

    private final PlatformCommissionRuleService service;

    public PlatformCommissionRuleController(PlatformCommissionRuleService service) {
        this.service = service;
    }

    @GetMapping
    public List<CommissionRuleResponse> list() {
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CommissionRuleResponse create(@Valid @RequestBody CreateCommissionRuleRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public CommissionRuleResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateCommissionRuleRequest request) {
        return service.update(id, request);
    }
}
