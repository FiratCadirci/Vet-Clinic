package org.codeflow.veterineryproject.auth.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.codeflow.veterineryproject.user.model.AppUser;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenServiceTest {

    private static final String SECRET = "test-secret-test-secret-test-secret-123456";

    private final JwtTokenService service = new JwtTokenService(SECRET, 900);

    @Test
    void shouldCreateAndParseTenantAccessToken() {
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID clinicId = UUID.randomUUID();
        AppUser user = new AppUser();
        user.setId(userId);
        user.setOrganizationId(orgId);

        String token = service.createAccessToken(user, List.of(clinicId), List.of("clients.read"));
        JwtTokenService.AccessTokenPayload payload = service.parseAccessToken(token);

        assertThat(payload.userId()).isEqualTo(userId);
        assertThat(payload.organizationId()).isEqualTo(orgId);
        assertThat(payload.clinicIds()).containsExactly(clinicId);
        assertThat(payload.permissions()).containsExactly("clients.read");
    }

    @Test
    void shouldRejectRevokedAccessToken() {
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setOrganizationId(UUID.randomUUID());

        String token = service.createAccessToken(user, List.of(), List.of());
        JwtTokenService.AccessTokenPayload payload = service.parseAccessToken(token);
        service.revokeAccessToken(payload.jti(), payload.expiresAt());

        assertThatThrownBy(() -> service.parseAccessToken(token)).isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void shouldCreateAndParsePlatformToken() {
        UUID adminId = UUID.randomUUID();
        String token = service.createPlatformAccessToken(adminId, "admin@test.com");

        JwtTokenService.PlatformTokenPayload payload = service.parsePlatformToken(token);

        assertThat(payload.adminId()).isEqualTo(adminId);
        assertThat(payload.email()).isEqualTo("admin@test.com");
    }

    @Test
    void shouldNotAcceptPlatformTokenAsTenantTokenAndViceVersa() {
        String platform = service.createPlatformAccessToken(UUID.randomUUID(), "a@b.com");
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setOrganizationId(UUID.randomUUID());
        String tenant = service.createAccessToken(user, List.of(), List.of());

        assertThatThrownBy(() -> service.parseAccessToken(platform)).isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> service.parsePlatformToken(tenant)).isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void shouldRejectGarbageToken() {
        assertThatThrownBy(() -> service.parseAccessToken("garbage")).isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> service.parsePlatformToken("garbage")).isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void shouldRejectExpiredToken() {
        String expired = Jwts.builder()
                .setSubject(UUID.randomUUID().toString())
                .setExpiration(Date.from(Instant.now().minusSeconds(60)))
                .claim("typ", "TENANT_ACCESS")
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThatThrownBy(() -> service.parseAccessToken(expired)).isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void shouldRejectTokenSignedWithAnotherKey() {
        JwtTokenService other = new JwtTokenService("another-secret-another-secret-another-1234", 900);
        String token = other.createPlatformAccessToken(UUID.randomUUID(), "a@b.com");

        assertThatThrownBy(() -> service.parsePlatformToken(token)).isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void refreshTokenShouldStartWithOrganizationId() {
        UUID orgId = UUID.randomUUID();
        assertThat(service.createRefreshToken(orgId)).startsWith(orgId + ".");
    }
}
