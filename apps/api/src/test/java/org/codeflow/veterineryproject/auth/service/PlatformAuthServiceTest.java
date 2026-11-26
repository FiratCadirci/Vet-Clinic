package org.codeflow.veterineryproject.auth.service;

import org.codeflow.veterineryproject.auth.dto.PlatformAuthResponse;
import org.codeflow.veterineryproject.auth.dto.PlatformLoginRequest;
import org.codeflow.veterineryproject.auth.model.PlatformAdmin;
import org.codeflow.veterineryproject.auth.repository.PlatformAdminRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlatformAuthServiceTest {

    private PlatformAdminRepository repository;
    private PasswordEncoder passwordEncoder;
    private JwtTokenService jwtTokenService;
    private PlatformAuthService service;
    private PlatformRefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        repository = mock(PlatformAdminRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtTokenService = mock(JwtTokenService.class);
        refreshTokenService = mock(PlatformRefreshTokenService.class);
        service = new PlatformAuthService(repository, passwordEncoder, jwtTokenService, refreshTokenService);
        when(jwtTokenService.getAccessTokenTtlSeconds()).thenReturn(900L);
    }

    @Test
    void shouldLoginWithValidCredentials() {
        PlatformAdmin admin = admin(true);
        when(repository.findByEmailIgnoreCase("admin@test.com")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("secret", "hash")).thenReturn(true);
        when(jwtTokenService.createPlatformAccessToken(admin.getId(), "admin@test.com")).thenReturn("tok");

        PlatformAuthResponse response = service.login(new PlatformLoginRequest(" admin@test.com ", "secret"));

        assertThat(response.accessToken()).isEqualTo("tok");
        assertThat(response.role()).isEqualTo("PLATFORM_ADMIN");
        assertThat(admin.getLastLoginAt()).isNotNull();
    }

    @Test
    void shouldRejectWrongPassword() {
        when(repository.findByEmailIgnoreCase("admin@test.com")).thenReturn(Optional.of(admin(true)));
        when(passwordEncoder.matches("bad", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.login(new PlatformLoginRequest("admin@test.com", "bad")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void shouldRejectInactiveAdminAndUnknownEmail() {
        when(repository.findByEmailIgnoreCase("admin@test.com")).thenReturn(Optional.of(admin(false)));
        when(passwordEncoder.matches("secret", "hash")).thenReturn(true);

        assertThatThrownBy(() -> service.login(new PlatformLoginRequest("admin@test.com", "secret")))
                .isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> service.login(new PlatformLoginRequest("none@test.com", "secret")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void loginShouldIssueRefreshToken() {
        PlatformAdmin admin = admin(true);
        when(repository.findByEmailIgnoreCase("admin@test.com")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("secret", "hash")).thenReturn(true);
        when(refreshTokenService.issue(admin.getId())).thenReturn("p.refresh");

        assertThat(service.login(new PlatformLoginRequest("admin@test.com", "secret")).refreshToken())
                .isEqualTo("p.refresh");
    }

    @Test
    void refreshShouldRotateAndIssueNewAccessToken() {
        PlatformAdmin admin = admin(true);
        when(refreshTokenService.rotate("old"))
                .thenReturn(new PlatformRefreshTokenService.Rotated(admin.getId(), "p.new"));
        when(repository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(jwtTokenService.createPlatformAccessToken(admin.getId(), "admin@test.com")).thenReturn("acc");

        PlatformAuthResponse response = service.refresh("old");

        assertThat(response.accessToken()).isEqualTo("acc");
        assertThat(response.refreshToken()).isEqualTo("p.new");
        assertThat(response.adminId()).isEqualTo(admin.getId());
    }

    @Test
    void refreshShouldRejectInactiveAdmin() {
        PlatformAdmin admin = admin(false);
        when(refreshTokenService.rotate("old"))
                .thenReturn(new PlatformRefreshTokenService.Rotated(admin.getId(), "p.new"));
        when(repository.findById(admin.getId())).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.refresh("old")).isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void refreshShouldPropagateInvalidTokenError() {
        when(refreshTokenService.rotate("bad")).thenThrow(new BadCredentialsException("x"));

        assertThatThrownBy(() -> service.refresh("bad")).isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void logoutShouldRevokeRefreshToken() {
        service.logout("p.token");

        verify(refreshTokenService).revokeByRaw("p.token");
    }

    @Test
    void meShouldReturnActiveAdmin() {
        PlatformAdmin admin = admin(true);
        when(jwtTokenService.parsePlatformToken("tok"))
                .thenReturn(new JwtTokenService.PlatformTokenPayload(admin.getId(), admin.getEmail()));
        when(repository.findById(admin.getId())).thenReturn(Optional.of(admin));

        assertThat(service.me("tok").adminId()).isEqualTo(admin.getId());
    }

    @Test
    void meShouldRejectInactiveOrMissingAdmin() {
        PlatformAdmin inactive = admin(false);
        when(jwtTokenService.parsePlatformToken("tok"))
                .thenReturn(new JwtTokenService.PlatformTokenPayload(inactive.getId(), inactive.getEmail()));
        when(repository.findById(inactive.getId())).thenReturn(Optional.of(inactive));
        assertThatThrownBy(() -> service.me("tok")).isInstanceOf(BadCredentialsException.class);

        UUID missing = UUID.randomUUID();
        when(jwtTokenService.parsePlatformToken("tok2"))
                .thenReturn(new JwtTokenService.PlatformTokenPayload(missing, "x@y.com"));
        when(repository.findById(missing)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.me("tok2")).isInstanceOf(BadCredentialsException.class);
    }

    private PlatformAdmin admin(boolean active) {
        PlatformAdmin admin = new PlatformAdmin();
        ReflectionTestUtils.setField(admin, "id", UUID.randomUUID());
        admin.setEmail("admin@test.com");
        admin.setDisplayName("Admin");
        admin.setPasswordHash("hash");
        admin.setIsActive(active);
        return admin;
    }
}
