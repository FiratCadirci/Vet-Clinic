package org.codeflow.veterineryproject.auth.service;

import org.codeflow.veterineryproject.auth.model.PlatformAdmin;
import org.codeflow.veterineryproject.auth.repository.PlatformAdminRepository;

import org.codeflow.veterineryproject.auth.dto.PlatformAuthResponse;
import org.codeflow.veterineryproject.auth.dto.PlatformLoginRequest;
import org.codeflow.veterineryproject.auth.service.JwtTokenService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
public class PlatformAuthService {

    private static final String INVALID_CREDENTIALS_MESSAGE = "Kimlik bilgileri hatalı.";

    private final PlatformAdminRepository platformAdminRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final PlatformRefreshTokenService platformRefreshTokenService;

    public PlatformAuthService(
            PlatformAdminRepository platformAdminRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService,
            PlatformRefreshTokenService platformRefreshTokenService
    ) {
        this.platformAdminRepository = platformAdminRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.platformRefreshTokenService = platformRefreshTokenService;
    }

    @Transactional
    public PlatformAuthResponse login(PlatformLoginRequest request) {
        PlatformAdmin admin = platformAdminRepository.findByEmailIgnoreCase(request.email().trim())
                .orElseThrow(() -> new BadCredentialsException(INVALID_CREDENTIALS_MESSAGE));

        if (!Boolean.TRUE.equals(admin.getIsActive()) || !passwordEncoder.matches(request.password(), admin.getPasswordHash())) {
            throw new BadCredentialsException(INVALID_CREDENTIALS_MESSAGE);
        }

        admin.setLastLoginAt(OffsetDateTime.now(ZoneOffset.UTC));
        platformAdminRepository.save(admin);

        String accessToken = jwtTokenService.createPlatformAccessToken(admin.getId(), admin.getEmail());
        return toResponse(admin, accessToken, platformRefreshTokenService.issue(admin.getId()));
    }

    @Transactional
    public PlatformAuthResponse refresh(String rawRefreshToken) {
        PlatformRefreshTokenService.Rotated rotated = platformRefreshTokenService.rotate(rawRefreshToken);
        PlatformAdmin admin = platformAdminRepository.findById(rotated.adminId())
                .filter(a -> Boolean.TRUE.equals(a.getIsActive()))
                .orElseThrow(() -> new BadCredentialsException(INVALID_CREDENTIALS_MESSAGE));
        String accessToken = jwtTokenService.createPlatformAccessToken(admin.getId(), admin.getEmail());
        return toResponse(admin, accessToken, rotated.rawToken());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        platformRefreshTokenService.revokeByRaw(rawRefreshToken);
    }
    public PlatformAuthResponse me(String accessToken) {
        JwtTokenService.PlatformTokenPayload payload = jwtTokenService.parsePlatformToken(accessToken);
        PlatformAdmin admin = platformAdminRepository.findById(payload.adminId())
                .orElseThrow(() -> new BadCredentialsException(INVALID_CREDENTIALS_MESSAGE));
        if (!Boolean.TRUE.equals(admin.getIsActive())) {
            throw new BadCredentialsException(INVALID_CREDENTIALS_MESSAGE);
        }
        return toResponse(admin, accessToken, null);
    }

    private PlatformAuthResponse toResponse(PlatformAdmin admin, String accessToken, String refreshToken) {
        return new PlatformAuthResponse(
                "Bearer",
                accessToken,
                refreshToken,
                jwtTokenService.getAccessTokenTtlSeconds(),
                admin.getId(),
                admin.getDisplayName(),
                admin.getEmail(),
                "PLATFORM_ADMIN"
        );
    }
}

