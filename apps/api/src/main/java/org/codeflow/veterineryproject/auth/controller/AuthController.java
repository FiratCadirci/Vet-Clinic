package org.codeflow.veterineryproject.auth.controller;

import jakarta.validation.Valid;
import org.codeflow.veterineryproject.auth.dto.AuthChangePasswordRequest;
import org.codeflow.veterineryproject.auth.dto.AuthLoginRequest;
import org.codeflow.veterineryproject.auth.dto.AuthLoginResponse;
import org.codeflow.veterineryproject.auth.dto.AuthLogoutRequest;
import org.codeflow.veterineryproject.auth.dto.AuthRefreshRequest;
import org.codeflow.veterineryproject.auth.security.JwtAuthenticatedUser;
import org.codeflow.veterineryproject.auth.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public AuthLoginResponse login(@Valid @RequestBody AuthLoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    public AuthLoginResponse refresh(@Valid @RequestBody AuthRefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(
            @AuthenticationPrincipal JwtAuthenticatedUser principal,
            @Valid @RequestBody AuthChangePasswordRequest request
    ) {
        authService.changePassword(principal.organizationId(), principal.userId(), principal.clinicId(), request);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(
            @AuthenticationPrincipal JwtAuthenticatedUser principal,
            @RequestBody(required = false) AuthLogoutRequest request
    ) {
        authService.logout(principal, request == null ? null : request.refreshToken());
    }

    @GetMapping("/me")
    public AuthLoginResponse me(@AuthenticationPrincipal JwtAuthenticatedUser principal) {
        return authService.me(principal.organizationId(), principal.userId(), principal.clinicId());
    }
}
