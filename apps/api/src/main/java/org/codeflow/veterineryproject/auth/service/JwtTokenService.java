package org.codeflow.veterineryproject.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import org.codeflow.veterineryproject.user.model.AppUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class JwtTokenService {

    private static final String INVALID_TOKEN_MESSAGE = "Kimlik doğrulama başarısız.";
    private final SecretKey secretKey;
    private final long accessTokenTtlSeconds;
    private final Map<String, Instant> revokedAccessTokens = new ConcurrentHashMap<>();

    public JwtTokenService(
            @Value("${app.security.jwt.secret}") String jwtSecret,
            @Value("${app.security.jwt.access-token-ttl-seconds:900}") long accessTokenTtlSeconds
    ) {
        this.secretKey = Keys.hmacShaKeyFor(normalizeSecret(jwtSecret));
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
    }

    public String createAccessToken(AppUser user, List<UUID> clinicIds, List<String> permissions) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(accessTokenTtlSeconds);
        List<String> clinics = clinicIds.stream().map(UUID::toString).toList();

        return Jwts.builder()
                .setId(UUID.randomUUID().toString())
                .setSubject(user.getId().toString())
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(expiresAt))
                .claim("org", user.getOrganizationId().toString())
                .claim("clinics", clinics)
                .claim("perms", permissions)
                .claim("mcp", Boolean.TRUE.equals(user.getMustChangePassword()))
                .claim("typ", "TENANT_ACCESS")
                .signWith(secretKey)
                .compact();
    }

    public String createRefreshToken(UUID organizationId) {
        String randomPart = UUID.randomUUID().toString().replace("-", "");
        return organizationId + "." + randomPart;
    }

    public String createPlatformAccessToken(UUID adminId, String email) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(accessTokenTtlSeconds);

        return Jwts.builder()
                .setSubject(adminId.toString())
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(expiresAt))
                .claim("email", email)
                .claim("role", "PLATFORM_ADMIN")
                .claim("typ", "PLATFORM_ACCESS")
                .signWith(secretKey)
                .compact();
    }

    public AccessTokenPayload parseAccessToken(String rawToken) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(rawToken)
                    .getPayload();

            String typ = String.valueOf(claims.get("typ"));
            if (!"TENANT_ACCESS".equals(typ)) {
                throw new BadCredentialsException(INVALID_TOKEN_MESSAGE);
            }
            String jti = claims.getId();
            if (jti != null && revokedAccessTokens.containsKey(jti)) {
                throw new BadCredentialsException(INVALID_TOKEN_MESSAGE);
            }

            UUID userId = UUID.fromString((String) claims.get("sub"));
            UUID organizationId = UUID.fromString((String) claims.get("org"));
            List<UUID> clinicIds = readUuidListClaim(claims, "clinics");
            List<String> permissions = readStringListClaim(claims, "perms");
            boolean mustChangePassword = Boolean.TRUE.equals(claims.get("mcp"));
            Instant expiresAt = claims.getExpiration() == null ? null : claims.getExpiration().toInstant();
            return new AccessTokenPayload(userId, organizationId, clinicIds, permissions, mustChangePassword, jti, expiresAt);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new BadCredentialsException(INVALID_TOKEN_MESSAGE);
        }
    }

    public PlatformTokenPayload parsePlatformToken(String rawToken) {
        try {
            Map<String, Object> claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(rawToken)
                    .getPayload();

            String typ = String.valueOf(claims.get("typ"));
            if (!"PLATFORM_ACCESS".equals(typ)) {
                throw new BadCredentialsException(INVALID_TOKEN_MESSAGE);
            }

            UUID adminId = UUID.fromString((String) claims.get("sub"));
            String email = String.valueOf(claims.get("email"));
            return new PlatformTokenPayload(adminId, email);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new BadCredentialsException(INVALID_TOKEN_MESSAGE);
        }
    }

    /** Erişim token'ını süresi dolana kadar bellek içi iptal listesine alır (tek instance, yeniden başlatmada sıfırlanır). */
    public void revokeAccessToken(String jti, Instant expiresAt) {
        if (jti == null) {
            return;
        }
        Instant now = Instant.now();
        revokedAccessTokens.values().removeIf(exp -> exp.isBefore(now));
        revokedAccessTokens.put(jti, expiresAt == null ? now.plusSeconds(accessTokenTtlSeconds) : expiresAt);
    }

    public long getAccessTokenTtlSeconds() {
        return accessTokenTtlSeconds;
    }

    private byte[] normalizeSecret(String rawSecret) {
        byte[] bytes = rawSecret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length >= 32) {
            return bytes;
        }
        String fallback = rawSecret + "vetclinic-secure-key-0123456789abcdef";
        return Arrays.copyOf(fallback.getBytes(StandardCharsets.UTF_8), 32);
    }

    private List<UUID> readUuidListClaim(Map<String, Object> claims, String claimName) {
        Object value = claims.get(claimName);
        if (!(value instanceof List<?> listValue)) {
            return List.of();
        }

        return listValue.stream()
                .map(String::valueOf)
                .map(UUID::fromString)
                .collect(Collectors.toList());
    }

    private List<String> readStringListClaim(Map<String, Object> claims, String claimName) {
        Object value = claims.get(claimName);
        if (!(value instanceof List<?> listValue)) {
            return List.of();
        }

        return listValue.stream()
                .map(String::valueOf)
                .collect(Collectors.toList());
    }

    public record AccessTokenPayload(
            UUID userId,
            UUID organizationId,
            List<UUID> clinicIds,
            List<String> permissions,
            boolean mustChangePassword,
            String jti,
            Instant expiresAt
    ) {
        public AccessTokenPayload(UUID userId, UUID organizationId, List<UUID> clinicIds, List<String> permissions, boolean mustChangePassword) {
            this(userId, organizationId, clinicIds, permissions, mustChangePassword, null, null);
        }

        public AccessTokenPayload(UUID userId, UUID organizationId, List<UUID> clinicIds, List<String> permissions) {
            this(userId, organizationId, clinicIds, permissions, false, null, null);
        }
    }

    public record PlatformTokenPayload(UUID adminId, String email) {
    }
}
