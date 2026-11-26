package org.codeflow.veterineryproject.auth.service;

import org.codeflow.veterineryproject.auth.model.PlatformRefreshToken;
import org.codeflow.veterineryproject.auth.repository.PlatformRefreshTokenRepository;
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
public class PlatformRefreshTokenService {

    private static final String INVALID_MESSAGE = "Kimlik doğrulama başarısız.";

    private final PlatformRefreshTokenRepository repository;
    private final long refreshTokenTtlSeconds;

    public PlatformRefreshTokenService(
            PlatformRefreshTokenRepository repository,
            @Value("${app.security.jwt.refresh-token-ttl-seconds:1209600}") long refreshTokenTtlSeconds
    ) {
        this.repository = repository;
        this.refreshTokenTtlSeconds = refreshTokenTtlSeconds;
    }

    public String issue(UUID adminId) {
        return create(adminId, UUID.randomUUID(), OffsetDateTime.now(ZoneOffset.UTC)).rawToken();
    }

    public Rotated rotate(String rawRefreshToken) {
        PlatformRefreshToken current = findByRaw(rawRefreshToken)
                .orElseThrow(() -> new BadCredentialsException(INVALID_MESSAGE));
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (current.getRevokedAt() != null) {
            revokeFamily(current.getTokenFamilyId(), now);
            throw new BadCredentialsException(INVALID_MESSAGE);
        }
        if (current.getExpiresAt().isBefore(now)) {
            throw new BadCredentialsException(INVALID_MESSAGE);
        }
        Created created = create(current.getPlatformAdminId(), current.getTokenFamilyId(), now);
        current.setRevokedAt(now);
        current.setLastUsedAt(now);
        current.setReplacedByTokenHash(created.hash());
        repository.save(current);
        return new Rotated(current.getPlatformAdminId(), created.rawToken());
    }

    public void revokeByRaw(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        findByRaw(rawRefreshToken)
                .ifPresent(token -> revokeFamily(token.getTokenFamilyId(), OffsetDateTime.now(ZoneOffset.UTC)));
    }

    private java.util.Optional<PlatformRefreshToken> findByRaw(String raw) {
        if (raw == null || raw.isBlank()) {
            return java.util.Optional.empty();
        }
        return repository.findByTokenHash(hash(raw.trim()));
    }

    private Created create(UUID adminId, UUID familyId, OffsetDateTime now) {
        String raw = "p." + UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        PlatformRefreshToken token = new PlatformRefreshToken();
        token.setPlatformAdminId(adminId);
        token.setTokenHash(hash(raw));
        token.setTokenFamilyId(familyId);
        token.setExpiresAt(now.plusSeconds(refreshTokenTtlSeconds));
        repository.save(token);
        return new Created(raw, token.getTokenHash());
    }

    private void revokeFamily(UUID familyId, OffsetDateTime at) {
        List<PlatformRefreshToken> active = repository.findByTokenFamilyIdAndRevokedAtIsNull(familyId);
        for (PlatformRefreshToken token : active) {
            token.setRevokedAt(at);
        }
        repository.saveAll(active);
    }

    private String hash(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algoritması bulunamadı.", ex);
        }
    }

    private record Created(String rawToken, String hash) {
    }

    public record Rotated(UUID adminId, String rawToken) {
    }
}
