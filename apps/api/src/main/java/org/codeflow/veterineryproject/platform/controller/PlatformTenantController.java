package org.codeflow.veterineryproject.platform.controller;

import jakarta.validation.Valid;
import org.codeflow.veterineryproject.auth.security.PlatformAdminPrincipal;
import org.codeflow.veterineryproject.platform.dto.PlatformDto.TenantResponse;
import org.codeflow.veterineryproject.platform.dto.PlatformDto.UpdateTenantSubscriptionRequest;
import org.codeflow.veterineryproject.platform.service.PlatformTenantService;
import org.codeflow.veterineryproject.subscription.dto.SubscriptionDto.RenewalRunResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Platform yöneticisi: klinikler (organizasyonlar) ve abonelik müdahalesi. Yalnız platform token'ı. */
@RestController
@RequestMapping("/api/platform")
@PreAuthorize("hasAuthority('" + PlatformAdminPrincipal.AUTHORITY + "')")
public class PlatformTenantController {

    private final PlatformTenantService service;

    public PlatformTenantController(PlatformTenantService service) {
        this.service = service;
    }

    @GetMapping("/tenants")
    public List<TenantResponse> list() {
        return service.list();
    }

    @PatchMapping("/tenants/{organizationId}/subscription")
    public TenantResponse updateSubscription(@PathVariable UUID organizationId,
                                             @Valid @RequestBody UpdateTenantSubscriptionRequest request) {
        return service.updateSubscription(organizationId, request);
    }

    /** Günlük abonelik kontrolünü (süresi dolan → PAST_DUE) elle tetikler. */
    @PostMapping("/subscriptions/renewal-check")
    public RenewalRunResponse runRenewalCheck() {
        return service.runRenewalCheck();
    }
}
