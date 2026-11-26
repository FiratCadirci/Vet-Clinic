package org.codeflow.veterineryproject.auth.controller;

import org.codeflow.veterineryproject.auth.service.PlatformAuthService;

import jakarta.validation.Valid;
import org.codeflow.veterineryproject.auth.dto.PlatformAuthResponse;
import org.codeflow.veterineryproject.auth.dto.PlatformLoginRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.codeflow.veterineryproject.auth.dto.PlatformRefreshRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/auth")
public class PlatformAuthController {

    private final PlatformAuthService platformAuthService;

    public PlatformAuthController(PlatformAuthService platformAuthService) {
        this.platformAuthService = platformAuthService;
    }

    @PostMapping("/login")
    public PlatformAuthResponse login(@Valid @RequestBody PlatformLoginRequest request) {
        return platformAuthService.login(request);
    }

    @PostMapping("/refresh")
    public PlatformAuthResponse refresh(@Valid @RequestBody PlatformRefreshRequest request) {
        return platformAuthService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestBody(required = false) PlatformRefreshRequest request) {
        platformAuthService.logout(request == null ? null : request.refreshToken());
    }

    @GetMapping("/me")
    public PlatformAuthResponse me(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        String token = extractBearerToken(authorization);
        return platformAuthService.me(token);
    }

    private String extractBearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new BadCredentialsException("Kimlik doğrulama başarısız.");
        }
        return authorization.substring("Bearer ".length()).trim();
    }
}

