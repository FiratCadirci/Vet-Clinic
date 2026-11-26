package org.codeflow.veterineryproject.auth.service;

import org.codeflow.veterineryproject.auth.dto.AuthLoginRequest;
import org.codeflow.veterineryproject.auth.dto.AuthLoginResponse;
import org.codeflow.veterineryproject.auth.repository.AuthReadRepository;
import org.codeflow.veterineryproject.auth.service.JwtTokenService;
import org.codeflow.veterineryproject.auth.service.RefreshTokenService;
import org.codeflow.veterineryproject.user.model.AppUser;
import org.codeflow.veterineryproject.user.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private AuthReadRepository authReadRepository;
    private AppUserRepository appUserRepository;
    private PasswordEncoder passwordEncoder;
    private JwtTokenService jwtTokenService;
    private RefreshTokenService refreshTokenService;
    private TransactionTemplate transactionTemplate;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        authReadRepository = mock(AuthReadRepository.class);
        appUserRepository = mock(AppUserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtTokenService = mock(JwtTokenService.class);
        refreshTokenService = mock(RefreshTokenService.class);
        transactionTemplate = mock(TransactionTemplate.class);

        doAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        }).when(transactionTemplate).execute(any());

        authService = new AuthService(
                authReadRepository,
                appUserRepository,
                passwordEncoder,
                jwtTokenService,
                refreshTokenService,
                transactionTemplate
        );
    }

    @Test
    void shouldLoginWithValidCredentials() {
        UUID organizationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        AppUser user = buildUser(userId, organizationId, true, "$2a$10$validhash");

        when(authReadRepository.resolveOrganizationIdByCode(1001)).thenReturn(organizationId);
        when(authReadRepository.resolveLoginUserId(organizationId, "vet@test.com")).thenReturn(userId);
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", "$2a$10$validhash")).thenReturn(true);
        when(authReadRepository.findActiveClinicIds(organizationId, userId)).thenReturn(List.of(UUID.randomUUID()));
        when(authReadRepository.findActivePermissionCodes(organizationId, userId)).thenReturn(List.of("clients.read"));
        when(jwtTokenService.createAccessToken(eq(user), any(), any())).thenReturn("access-token");
        when(jwtTokenService.getAccessTokenTtlSeconds()).thenReturn(900L);
        when(refreshTokenService.issue(organizationId, userId))
                .thenReturn(new RefreshTokenService.IssuedRefreshToken("refresh-token", UUID.randomUUID()));

        AuthLoginResponse response = authService.login(new AuthLoginRequest(1001, "vet@test.com", "secret"));

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.organizationId()).isEqualTo(organizationId);
        assertThat(response.userId()).isEqualTo(userId);
    }

    @Test
    void shouldRejectWhenPasswordIsInvalid() {
        UUID organizationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        AppUser user = buildUser(userId, organizationId, true, "$2a$10$validhash");

        when(authReadRepository.resolveOrganizationIdByCode(1001)).thenReturn(organizationId);
        when(authReadRepository.resolveLoginUserId(organizationId, "vet@test.com")).thenReturn(userId);
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "$2a$10$validhash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new AuthLoginRequest(1001, "vet@test.com", "wrong")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void shouldRejectWhenUserIsInactive() {
        UUID organizationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        AppUser user = buildUser(userId, organizationId, false, "$2a$10$validhash");

        when(authReadRepository.resolveOrganizationIdByCode(1001)).thenReturn(organizationId);
        when(authReadRepository.resolveLoginUserId(organizationId, "vet@test.com")).thenReturn(userId);
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", "$2a$10$validhash")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new AuthLoginRequest(1001, "vet@test.com", "secret")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void shouldRejectWhenOrganizationCodeIsInvalid() {
        when(authReadRepository.resolveOrganizationIdByCode(9999)).thenReturn(null);

        assertThatThrownBy(() -> authService.login(new AuthLoginRequest(9999, "vet@test.com", "secret")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void shouldRefreshAndIssueNewTokens() {
        UUID organizationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        AppUser user = buildUser(userId, organizationId, true, "h");

        when(refreshTokenService.extractOrganizationId("r.old")).thenReturn(organizationId);
        when(refreshTokenService.rotate("r.old"))
                .thenReturn(new RefreshTokenService.RotatedRefreshToken(organizationId, userId, "r.new"));
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(user));
        when(authReadRepository.findActiveClinicIds(organizationId, userId)).thenReturn(List.of());
        when(authReadRepository.findActivePermissionCodes(organizationId, userId)).thenReturn(List.of());
        when(jwtTokenService.createAccessToken(eq(user), any(), any())).thenReturn("new-access");

        AuthLoginResponse response = authService.refresh("r.old");

        assertThat(response.accessToken()).isEqualTo("new-access");
        assertThat(response.refreshToken()).isEqualTo("r.new");
    }

    @Test
    void shouldRejectRefreshWhenUserInactive() {
        UUID organizationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(refreshTokenService.extractOrganizationId("r.old")).thenReturn(organizationId);
        when(refreshTokenService.rotate("r.old"))
                .thenReturn(new RefreshTokenService.RotatedRefreshToken(organizationId, userId, "r.new"));
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(buildUser(userId, organizationId, false, "h")));

        assertThatThrownBy(() -> authService.refresh("r.old")).isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void meShouldReturnActiveClinicOnly() {
        UUID organizationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID clinicId = UUID.randomUUID();
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(buildUser(userId, organizationId, true, "h")));
        when(authReadRepository.findActiveClinicIds(organizationId, userId)).thenReturn(List.of(clinicId, UUID.randomUUID()));
        when(authReadRepository.findActivePermissionCodes(organizationId, userId)).thenReturn(List.of("clients.read"));

        AuthLoginResponse response = authService.me(organizationId, userId, clinicId);

        assertThat(response.clinicIds()).containsExactly(clinicId);
        assertThat(response.accessToken()).isNull();
    }

    @Test
    void meShouldRejectUnknownUser() {
        UUID userId = UUID.randomUUID();
        when(appUserRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.me(UUID.randomUUID(), userId, null)).isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void logoutShouldRevokeAccessTokenAndRefreshFamily() {
        UUID organizationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID clinicId = UUID.randomUUID();
        java.time.Instant exp = java.time.Instant.now().plusSeconds(600);
        doAnswer(invocation -> {
            java.util.function.Consumer<org.springframework.transaction.TransactionStatus> consumer = invocation.getArgument(0);
            consumer.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
        org.codeflow.veterineryproject.auth.security.JwtAuthenticatedUser principal =
                new org.codeflow.veterineryproject.auth.security.JwtAuthenticatedUser(
                        userId, organizationId, clinicId, List.of(clinicId), List.of(), "jti-1", exp);

        authService.logout(principal, "refresh-token");

        org.mockito.Mockito.verify(jwtTokenService).revokeAccessToken("jti-1", exp);
        org.mockito.Mockito.verify(refreshTokenService).revokeFamilyOfToken(organizationId, userId, "refresh-token");
    }

    private AppUser buildUser(UUID userId, UUID organizationId, boolean active, String passwordHash) {
        AppUser user = new AppUser();
        user.setId(userId);
        user.setOrganizationId(organizationId);
        user.setEmail("vet@test.com");
        user.setDisplayName("Vet User");
        user.setPasswordHash(passwordHash);
        user.setIsActive(active);
        return user;
    }
}

