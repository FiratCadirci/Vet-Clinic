package org.codeflow.veterineryproject.subscription.controller;

import jakarta.validation.Valid;
import org.codeflow.veterineryproject.auth.security.Permission;
import org.codeflow.veterineryproject.subscription.dto.PlanDto.PlanOption;
import org.codeflow.veterineryproject.subscription.dto.SubscriptionDto.BillingEventResponse;
import org.codeflow.veterineryproject.subscription.dto.SubscriptionDto.ChangePlanRequest;
import org.codeflow.veterineryproject.subscription.dto.SubscriptionDto.CurrentSubscriptionResponse;
import org.codeflow.veterineryproject.subscription.dto.SubscriptionDto.PaymentResult;
import org.codeflow.veterineryproject.subscription.service.SubscriptionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Kliniğin aboneliği. Okuma subscriptions.read, değişiklik subscriptions.manage (klinik yöneticisi) ister. */
@RestController
@RequestMapping("/api/v1/subscription")
public class SubscriptionController {

    private static final String READ = "hasAuthority('" + Permission.SUBSCRIPTIONS_READ + "')";
    private static final String MANAGE = "hasAuthority('" + Permission.SUBSCRIPTIONS_MANAGE + "')";

    private final SubscriptionService service;

    public SubscriptionController(SubscriptionService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(READ)
    public CurrentSubscriptionResponse current() {
        return service.current();
    }

    @GetMapping("/plans")
    @PreAuthorize(READ)
    public List<PlanOption> plans() {
        return service.plans();
    }

    @GetMapping("/payments")
    @PreAuthorize(READ)
    public List<BillingEventResponse> payments() {
        return service.payments();
    }

    @PostMapping("/change")
    @PreAuthorize(MANAGE)
    public CurrentSubscriptionResponse change(@RequestHeader("X-Clinic-Id") String clinicId,
                                              @Valid @RequestBody ChangePlanRequest request) {
        return service.changePlan(request.planId());
    }

    @PostMapping("/pay")
    @PreAuthorize(MANAGE)
    public PaymentResult pay(@RequestHeader("X-Clinic-Id") String clinicId) {
        return service.pay();
    }

    @PostMapping("/cancel")
    @PreAuthorize(MANAGE)
    public CurrentSubscriptionResponse cancel(@RequestHeader("X-Clinic-Id") String clinicId) {
        return service.cancel();
    }
}
