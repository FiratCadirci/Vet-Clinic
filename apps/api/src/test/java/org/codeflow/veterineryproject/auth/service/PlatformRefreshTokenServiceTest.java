package org.codeflow.veterineryproject.auth.service;

import org.codeflow.veterineryproject.auth.model.PlatformRefreshToken;
import org.codeflow.veterineryproject.auth.repository.PlatformRefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.BadCredentialsException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlatformRefreshTokenServiceTest {

    private PlatformRefreshTokenRepository repository;
    private PlatformRefreshTokenService service;

    @BeforeEach
    void setUp() {
        repository = mock(PlatformRefreshTokenRepository.class);
        service = new PlatformRefreshTokenService(repository, 1209600L);
    }

    @Test
    void issueShouldStoreOnlyHashOfRawToken() {
        UUID adminId = UUID.randomUUID();

        String raw = service.issue(adminId);

        ArgumentCaptor<PlatformRefreshToken> captor = ArgumentCaptor.forClass(PlatformRefreshToken.class);
        verify(repository).save(captor.capture());
        PlatformRefreshToken saved = captor.getValue();
        assertThat(raw).startsWith("p.").hasSize(66);
        assertThat(saved.getTokenHash()).isNotEqualTo(raw).hasSize(64);
        assertThat(saved.getPlatformAdminId()).isEqualTo(adminId);
        assertThat(saved.getRevokedAt()).isNull();
        assertThat(saved.getExpiresAt()).isAfter(OffsetDateTime.now(ZoneOffset.UTC).plusDays(13));
    }

    @Test
    void rotateShouldRevokeCurrentAndIssueNewTokenInSameFamily() {
        UUID adminId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        PlatformRefreshToken current = token(adminId, familyId, OffsetDateTime.now(ZoneOffset.UTC).plusDays(1), null);
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.of(current));

        PlatformRefreshTokenService.Rotated rotated = service.rotate("p.raw");

        assertThat(rotated.adminId()).isEqualTo(adminId);
        assertThat(rotated.rawToken()).startsWith("p.").isNotEqualTo("p.raw");
        assertThat(current.getRevokedAt()).isNotNull();
        assertThat(current.getReplacedByTokenHash()).isNotBlank();
        ArgumentCaptor<PlatformRefreshToken> captor = ArgumentCaptor.forClass(PlatformRefreshToken.class);
        verify(repository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues()).anyMatch(t -> t != current && familyId.equals(t.getTokenFamilyId()));
    }

    @Test
    void rotateShouldRejectUnknownToken() {
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rotate("p.unknown")).isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> service.rotate(null)).isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void rotateShouldRejectExpiredToken() {
        PlatformRefreshToken expired = token(UUID.randomUUID(), UUID.randomUUID(),
                OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1), null);
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.rotate("p.raw")).isInstanceOf(BadCredentialsException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void rotateShouldRevokeWholeFamilyWhenRevokedTokenIsReused() {
        UUID familyId = UUID.randomUUID();
        PlatformRefreshToken reused = token(UUID.randomUUID(), familyId,
                OffsetDateTime.now(ZoneOffset.UTC).plusDays(1), OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(5));
        PlatformRefreshToken sibling = token(reused.getPlatformAdminId(), familyId,
                OffsetDateTime.now(ZoneOffset.UTC).plusDays(1), null);
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.of(reused));
        when(repository.findByTokenFamilyIdAndRevokedAtIsNull(familyId)).thenReturn(List.of(sibling));

        assertThatThrownBy(() -> service.rotate("p.raw")).isInstanceOf(BadCredentialsException.class);

        assertThat(sibling.getRevokedAt()).isNotNull();
    }

    @Test
    void revokeByRawShouldRevokeActiveTokensOfFamily() {
        UUID familyId = UUID.randomUUID();
        PlatformRefreshToken current = token(UUID.randomUUID(), familyId,
                OffsetDateTime.now(ZoneOffset.UTC).plusDays(1), null);
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.of(current));
        when(repository.findByTokenFamilyIdAndRevokedAtIsNull(familyId)).thenReturn(List.of(current));

        service.revokeByRaw("p.raw");

        assertThat(current.getRevokedAt()).isNotNull();
    }

    @Test
    void revokeByRawShouldIgnoreBlankOrUnknownToken() {
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        service.revokeByRaw(null);
        service.revokeByRaw("  ");
        service.revokeByRaw("p.unknown");

        verify(repository, never()).findByTokenFamilyIdAndRevokedAtIsNull(any());
    }

    private PlatformRefreshToken token(UUID adminId, UUID familyId, OffsetDateTime expiresAt, OffsetDateTime revokedAt) {
        PlatformRefreshToken token = new PlatformRefreshToken();
        token.setPlatformAdminId(adminId);
        token.setTokenFamilyId(familyId);
        token.setTokenHash("hash");
        token.setExpiresAt(expiresAt);
        token.setRevokedAt(revokedAt);
        return token;
    }
}
