package org.codeflow.veterineryproject.organization.controller;

import jakarta.validation.Valid;
import org.codeflow.veterineryproject.auth.dto.AuthLoginResponse;
import org.codeflow.veterineryproject.organization.dto.OrganizationDto.SignupRequest;
import org.codeflow.veterineryproject.organization.service.ClinicSignupService;
import org.codeflow.veterineryproject.subscription.dto.PlanDto.PlanOption;
import org.codeflow.veterineryproject.subscription.service.PlanLimitService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Oturum öncesi klinik kaydı (public). Yanıt, girişteki gibi oturum açmış AuthLoginResponse'tur. */
@RestController
@RequestMapping("/api/auth/signup")
public class ClinicSignupController {

    private final ClinicSignupService signupService;
    private final PlanLimitService planLimitService;

    public ClinicSignupController(ClinicSignupService signupService, PlanLimitService planLimitService) {
        this.signupService = signupService;
        this.planLimitService = planLimitService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AuthLoginResponse signup(@Valid @RequestBody SignupRequest request) {
        return signupService.signup(request);
    }

    /** Kayıt ekranındaki paket seçimi için aktif paketler. */
    @GetMapping("/plans")
    public List<PlanOption> plans() {
        return planLimitService.activePlans();
    }
}
