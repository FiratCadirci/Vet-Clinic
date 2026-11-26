package org.codeflow.veterineryproject.auth.controller;

import jakarta.validation.Valid;
import org.codeflow.veterineryproject.auth.dto.UnifiedLoginRequest;
import org.codeflow.veterineryproject.auth.dto.UnifiedLoginResponse;
import org.codeflow.veterineryproject.auth.service.UnifiedAuthService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class UnifiedAuthController {

    private final UnifiedAuthService unifiedAuthService;

    public UnifiedAuthController(UnifiedAuthService unifiedAuthService) {
        this.unifiedAuthService = unifiedAuthService;
    }

    @org.springframework.web.bind.annotation.GetMapping("/organizations")
    public java.util.List<org.codeflow.veterineryproject.auth.dto.OrganizationOption> organizations() {
        return unifiedAuthService.listOrganizations();
    }

    @PostMapping("/sign-in")
    public UnifiedLoginResponse signIn(@Valid @RequestBody UnifiedLoginRequest request) {
        return unifiedAuthService.signIn(request);
    }
}
