package org.codeflow.veterineryproject.auth.service;

import org.codeflow.veterineryproject.auth.model.RefreshToken;
import org.codeflow.veterineryproject.auth.repository.RefreshTokenRepository;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefreshTokenServiceTest {

    private RefreshTokenRepository repository;
    private RefreshTokenService service;
    private final UUID orgId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        repository = mock(RefreshTokenRepository.class);
        service = new RefreshTokenService(repository, 3600);
    }

    @Test
    void shouldIssueTokenAndStoreOnlyHash() {
        RefreshTokenService.IssuedRefreshToken issued = service.issue(orgId, userId);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(captor.capture());
        assertThat(issued.rawToken()).startsWith(orgId + ".");
        assertThat(captor.getValue().getTokenHash()).isNotEqualTo(issued.rawToken()).hasSize(64);
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
        assertThat(captor.getValue().getTokenFamilyId()).isEqualTo(issued.tokenFamilyId());
    }

    @Test
    void shouldRotateValidTokenAndRevokeOld() {
        RefreshToken stored = stored(OffsetDateTime.now(ZoneOffset.UTC).plusHours(1), null);
        when(repository.findByOrganizationIdAndTokenHash(eq(orgId), anyString())).thenReturn(Optional.of(stored));

        RefreshTokenService.RotatedRefreshToken rotated = service.rotate(orgId + ".abc");

        assertThat(rotated.userId()).isEqualTo(userId);
        assertThat(rotated.rawToken()).startsWith(orgId + ".");
        assertThat(stored.getRevokedAt()).isNotNull();
        assertThat(stored.getReplacedByTokenHash()).isNotNull();
    }

    @Test
    void shouldRejectUnknownToken() {
        when(repository.findByOrganizationIdAndTokenHash(eq(orgId), anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rotate(orgId + ".abc")).isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void shouldRejectExpiredToken() {
        RefreshToken stored = stored(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1), null);
        when(repository.findByOrganizationIdAndTokenHash(eq(orgId), anyString())).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service.rotate(orgId + ".abc")).isInstanceOf(BadCredentialsException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void reuseOfRevokedTokenShouldRevokeWholeFamily() {
        RefreshToken revoked = stored(OffsetDateTime.now(ZoneOffset.UTC).plusHours(1), OffsetDateTime.now(ZoneOffset.UTC));
        RefreshToken sibling = stored(OffsetDateTime.now(ZoneOffset.UTC).plusHours(1), null);
        when(repository.findByOrganizationIdAndTokenHash(eq(orgId), anyString())).thenReturn(Optional.of(revoked));
        when(repository.findByOrganizationIdAndTokenFamilyIdAndRevokedAtIsNull(eq(orgId), any())).thenReturn(List.of(sibling));

        assertThatThrownBy(() -> service.rotate(orgId + ".abc")).isInstanceOf(BadCredentialsException.class);

        assertThat(sibling.getRevokedAt()).isNotNull();
    }

    @Test
    void shouldRejectMalformedTokens() {
        assertThatThrownBy(() -> service.extractOrganizationId(null)).isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> service.extractOrganizationId("nodot")).isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> service.extractOrganizationId("not-a-uuid.abc")).isInstanceOf(BadCredentialsException.class);
    }

    private RefreshToken stored(OffsetDateTime expiresAt, OffsetDateTime revokedAt) {
        RefreshToken token = new RefreshToken();
        token.setOrganizationId(orgId);
        token.setUserId(userId);
        token.setTokenHash("hash");
        token.setTokenFamilyId(UUID.randomUUID());
        token.setExpiresAt(expiresAt);
        token.setRevokedAt(revokedAt);
        return token;
    }
}
