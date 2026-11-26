package org.codeflow.veterineryproject.auth.service;

import org.codeflow.veterineryproject.auth.dto.AuthChangePasswordRequest;
import org.codeflow.veterineryproject.auth.dto.AuthLoginRequest;
import org.codeflow.veterineryproject.auth.dto.AuthLoginResponse;
import org.codeflow.veterineryproject.auth.repository.AuthReadRepository;
import org.codeflow.veterineryproject.auth.security.JwtAuthenticatedUser;
import org.codeflow.veterineryproject.auth.service.JwtTokenService;
import org.codeflow.veterineryproject.auth.service.RefreshTokenService;
import org.codeflow.veterineryproject.common.tenant.TenantContext;
import org.codeflow.veterineryproject.user.model.AppUser;
import org.codeflow.veterineryproject.user.repository.AppUserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class AuthService {

    private static final String INVALID_CREDENTIALS_MESSAGE = "Kimlik bilgileri hatalı.";

    private final AuthReadRepository authReadRepository;
    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final RefreshTokenService refreshTokenService;
    private final TransactionTemplate transactionTemplate;

    public AuthService(
            AuthReadRepository authReadRepository,
            AppUserRepository appUserRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService,
            RefreshTokenService refreshTokenService,
            TransactionTemplate transactionTemplate
    ) {
        this.authReadRepository = authReadRepository;
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.refreshTokenService = refreshTokenService;
        this.transactionTemplate = transactionTemplate;
    }

    public AuthLoginResponse login(AuthLoginRequest request) {
        UUID organizationId = authReadRepository.resolveOrganizationIdByCode(request.organizationCode());
        if (organizationId == null) {
            throw new BadCredentialsException(INVALID_CREDENTIALS_MESSAGE);
        }

        UUID userId = authReadRepository.resolveLoginUserId(organizationId, request.email().trim());
        if (userId == null) {
            throw new BadCredentialsException(INVALID_CREDENTIALS_MESSAGE);
        }

        try (TenantContext.Scope ignored = TenantContext.withTenant(organizationId, userId, null)) {
            return transactionTemplate.execute(status -> authenticateInTenant(organizationId, userId, request.password()));
        }
    }

    public AuthLoginResponse loginResolved(UUID organizationId, UUID userId, String password) {
        try (TenantContext.Scope ignored = TenantContext.withTenant(organizationId, userId, null)) {
            return transactionTemplate.execute(status -> authenticateInTenant(organizationId, userId, password));
        }
    }

    public boolean credentialsMatch(UUID organizationId, UUID userId, String password) {
        try (TenantContext.Scope ignored = TenantContext.withTenant(organizationId, userId, null)) {
            return Boolean.TRUE.equals(transactionTemplate.execute(status -> appUserRepository.findById(userId)
                    .map(u -> Boolean.TRUE.equals(u.getIsActive()) && passwordEncoder.matches(password, u.getPasswordHash()))
                    .orElse(false)));
        }
    }

    private AuthLoginResponse authenticateInTenant(UUID organizationId, UUID userId, String password) {
        AppUser user = appUserRepository
                .findById(userId)
                .orElseThrow(() -> new BadCredentialsException(INVALID_CREDENTIALS_MESSAGE));

        if (!Boolean.TRUE.equals(user.getIsActive()) || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BadCredentialsException(INVALID_CREDENTIALS_MESSAGE);
        }

        user.setLastLoginAt(OffsetDateTime.now(ZoneOffset.UTC));
        appUserRepository.save(user);

        List<UUID> clinicIds = authReadRepository.findActiveClinicIds(organizationId, user.getId());
        List<String> permissions = authReadRepository.findActivePermissionCodes(organizationId, user.getId());

        String accessToken = jwtTokenService.createAccessToken(user, clinicIds, permissions);
        RefreshTokenService.IssuedRefreshToken refreshToken = refreshTokenService.issue(organizationId, user.getId());

        return new AuthLoginResponse(
                "Bearer",
                accessToken,
                refreshToken.rawToken(),
                jwtTokenService.getAccessTokenTtlSeconds(),
                organizationId,
                user.getId(),
                user.getDisplayName(),
                clinicIds,
                permissions,
                Boolean.TRUE.equals(user.getMustChangePassword()),
                authReadRepository.findActiveRoles(organizationId, user.getId()),
                authReadRepository.findActiveClinics(organizationId, user.getId())
        );
    }

    public AuthLoginResponse refresh(String refreshToken) {
        UUID organizationId = refreshTokenService.extractOrganizationId(refreshToken);
        RefreshTokenService.RotatedRefreshToken rotated;
        try (TenantContext.Scope ignored = TenantContext.withTenant(organizationId, null, null)) {
            rotated = transactionTemplate.execute(status -> refreshTokenService.rotate(refreshToken));
        }

        try (TenantContext.Scope ignored = TenantContext.withTenant(rotated.organizationId(), rotated.userId(), null)) {
            return transactionTemplate.execute(status -> issueTokensAfterRotation(rotated));
        }
    }

    public void logout(JwtAuthenticatedUser principal, String refreshToken) {
        jwtTokenService.revokeAccessToken(principal.tokenId(), principal.tokenExpiresAt());
        try (TenantContext.Scope ignored = TenantContext.withTenant(principal.organizationId(), principal.userId(), principal.clinicId())) {
            transactionTemplate.executeWithoutResult(status ->
                    refreshTokenService.revokeFamilyOfToken(principal.organizationId(), principal.userId(), refreshToken));
        }
    }

    public AuthLoginResponse me(UUID organizationId, UUID userId, UUID clinicId) {
        try (TenantContext.Scope ignored = TenantContext.withTenant(organizationId, userId, clinicId)) {
            return transactionTemplate.execute(status -> readCurrentUser(organizationId, userId, clinicId));
        }
    }

    public void changePassword(UUID organizationId, UUID userId, UUID clinicId, AuthChangePasswordRequest request) {
        try (TenantContext.Scope ignored = TenantContext.withTenant(organizationId, userId, clinicId)) {
            transactionTemplate.executeWithoutResult(status -> applyPasswordChange(organizationId, userId, request));
        }
    }

    private void applyPasswordChange(UUID organizationId, UUID userId, AuthChangePasswordRequest request) {
        AppUser user = appUserRepository
                .findById(userId)
                .orElseThrow(() -> new BadCredentialsException(INVALID_CREDENTIALS_MESSAGE));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException(INVALID_CREDENTIALS_MESSAGE);
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Yeni şifre mevcut şifreyle aynı olamaz.");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(false);
        user.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        appUserRepository.save(user);
        refreshTokenService.revokeAllForUser(organizationId, userId);
    }

    private AuthLoginResponse issueTokensAfterRotation(RefreshTokenService.RotatedRefreshToken rotated) {

        AppUser user = appUserRepository
                .findById(rotated.userId())
                .orElseThrow(() -> new BadCredentialsException(INVALID_CREDENTIALS_MESSAGE));

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new BadCredentialsException(INVALID_CREDENTIALS_MESSAGE);
        }

        List<UUID> clinicIds = authReadRepository.findActiveClinicIds(rotated.organizationId(), user.getId());
        List<String> permissions = authReadRepository.findActivePermissionCodes(rotated.organizationId(), user.getId());
        String accessToken = jwtTokenService.createAccessToken(user, clinicIds, permissions);

        return new AuthLoginResponse(
                "Bearer",
                accessToken,
                rotated.rawToken(),
                jwtTokenService.getAccessTokenTtlSeconds(),
                rotated.organizationId(),
                user.getId(),
                user.getDisplayName(),
                clinicIds,
                permissions,
                Boolean.TRUE.equals(user.getMustChangePassword()),
                authReadRepository.findActiveRoles(rotated.organizationId(), user.getId()),
                authReadRepository.findActiveClinics(rotated.organizationId(), user.getId())
        );
    }

    private AuthLoginResponse readCurrentUser(UUID organizationId, UUID userId, UUID clinicId) {
        AppUser user = appUserRepository
                .findById(userId)
                .orElseThrow(() -> new BadCredentialsException(INVALID_CREDENTIALS_MESSAGE));

        List<UUID> clinicIds = authReadRepository.findActiveClinicIds(organizationId, userId);
        List<String> permissions = authReadRepository.findActivePermissionCodes(organizationId, userId);

        return new AuthLoginResponse(
                "Bearer",
                null,
                null,
                jwtTokenService.getAccessTokenTtlSeconds(),
                organizationId,
                userId,
                user.getDisplayName(),
                clinicId == null ? clinicIds : List.of(clinicId),
                permissions,
                Boolean.TRUE.equals(user.getMustChangePassword()),
                authReadRepository.findActiveRoles(organizationId, userId),
                clinicId == null
                        ? authReadRepository.findActiveClinics(organizationId, userId)
                        : authReadRepository.findActiveClinics(organizationId, userId).stream()
                                .filter(c -> clinicId.equals(c.id())).toList()
        );
    }
}
