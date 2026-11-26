package org.codeflow.veterineryproject.auth.service;

import org.codeflow.veterineryproject.auth.model.RefreshToken;
import org.codeflow.veterineryproject.auth.repository.RefreshTokenRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private static final String INVALID_REFRESH_TOKEN_MESSAGE = "Kimlik doğrulama başarısız.";

    private final RefreshTokenRepository refreshTokenRepository;
    private final long refreshTokenTtlSeconds;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            @Value("${app.security.jwt.refresh-token-ttl-seconds:1209600}") long refreshTokenTtlSeconds
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenTtlSeconds = refreshTokenTtlSeconds;
    }

    public IssuedRefreshToken issue(UUID organizationId, UUID userId) {
        String rawToken = createRawRefreshToken(organizationId);
        RefreshToken token = new RefreshToken();
        token.setOrganizationId(organizationId);
        token.setUserId(userId);
        token.setTokenHash(hash(rawToken));
        token.setTokenFamilyId(UUID.randomUUID());
        token.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(refreshTokenTtlSeconds));
        refreshTokenRepository.save(token);
        return new IssuedRefreshToken(rawToken, token.getTokenFamilyId());
    }

    public RotatedRefreshToken rotate(String rawRefreshToken) {
        UUID organizationId = extractOrganizationId(rawRefreshToken);
        String currentHash = hash(rawRefreshToken);

        RefreshToken currentToken = refreshTokenRepository
                .findByOrganizationIdAndTokenHash(organizationId, currentHash)
                .orElseThrow(() -> new BadCredentialsException(INVALID_REFRESH_TOKEN_MESSAGE));

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (currentToken.getRevokedAt() != null) {
            revokeTokenFamily(organizationId, currentToken.getTokenFamilyId(), now);
            throw new BadCredentialsException(INVALID_REFRESH_TOKEN_MESSAGE);
        }

        if (currentToken.getExpiresAt().isBefore(now)) {
            throw new BadCredentialsException(INVALID_REFRESH_TOKEN_MESSAGE);
        }

        String newRawToken = createRawRefreshToken(organizationId);
        String newHash = hash(newRawToken);

        RefreshToken rotatedToken = new RefreshToken();
        rotatedToken.setOrganizationId(organizationId);
        rotatedToken.setUserId(currentToken.getUserId());
        rotatedToken.setTokenHash(newHash);
        rotatedToken.setTokenFamilyId(currentToken.getTokenFamilyId());
        rotatedToken.setExpiresAt(now.plusSeconds(refreshTokenTtlSeconds));
        refreshTokenRepository.save(rotatedToken);

        currentToken.setRevokedAt(now);
        currentToken.setLastUsedAt(now);
        currentToken.setReplacedByTokenHash(newHash);
        refreshTokenRepository.save(currentToken);

        return new RotatedRefreshToken(organizationId, currentToken.getUserId(), newRawToken);
    }

    public void revokeFamilyOfToken(UUID organizationId, UUID userId, String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokenRepository
                .findByOrganizationIdAndTokenHash(organizationId, hash(rawRefreshToken.trim()))
                .filter(token -> userId.equals(token.getUserId()))
                .ifPresent(token -> revokeTokenFamily(organizationId, token.getTokenFamilyId(), OffsetDateTime.now(ZoneOffset.UTC)));
    }

    public void revokeAllForUser(UUID organizationId, UUID userId) {
        List<RefreshToken> active = refreshTokenRepository.findByOrganizationIdAndUserIdAndRevokedAtIsNull(organizationId, userId);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        for (RefreshToken token : active) {
            token.setRevokedAt(now);
        }
        refreshTokenRepository.saveAll(active);
    }

    public UUID extractOrganizationId(String rawRefreshToken) {
        if (rawRefreshToken == null || !rawRefreshToken.contains(".")) {
            throw new BadCredentialsException(INVALID_REFRESH_TOKEN_MESSAGE);
        }

        String[] parts = rawRefreshToken.split("\\.", 2);
        try {
            return UUID.fromString(parts[0]);
        } catch (IllegalArgumentException ex) {
            throw new BadCredentialsException(INVALID_REFRESH_TOKEN_MESSAGE);
        }
    }

    public long getRefreshTokenTtlSeconds() {
        return refreshTokenTtlSeconds;
    }

    private void revokeTokenFamily(UUID organizationId, UUID tokenFamilyId, OffsetDateTime revokedAt) {
        List<RefreshToken> activeFamilyTokens = refreshTokenRepository
                .findByOrganizationIdAndTokenFamilyIdAndRevokedAtIsNull(organizationId, tokenFamilyId);
        for (RefreshToken token : activeFamilyTokens) {
            token.setRevokedAt(revokedAt);
        }
        refreshTokenRepository.saveAll(activeFamilyTokens);
    }

    private String createRawRefreshToken(UUID organizationId) {
        String randomPart = UUID.randomUUID().toString().replace("-", "");
        return organizationId + "." + randomPart;
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algoritması bulunamadı.", ex);
        }
    }

    public record IssuedRefreshToken(String rawToken, UUID tokenFamilyId) {
    }

    public record RotatedRefreshToken(UUID organizationId, UUID userId, String rawToken) {
    }
}

