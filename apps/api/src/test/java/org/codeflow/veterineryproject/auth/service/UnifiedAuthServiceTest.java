package org.codeflow.veterineryproject.auth.service;

import org.codeflow.veterineryproject.auth.dto.AuthLoginCandidate;
import org.codeflow.veterineryproject.auth.dto.AuthLoginResponse;
import org.codeflow.veterineryproject.auth.dto.AuthPanel;
import org.codeflow.veterineryproject.auth.dto.AuthRoleSummary;
import org.codeflow.veterineryproject.auth.dto.PlatformAuthResponse;
import org.codeflow.veterineryproject.auth.dto.PlatformLoginRequest;
import org.codeflow.veterineryproject.auth.dto.UnifiedLoginRequest;
import org.codeflow.veterineryproject.auth.dto.UnifiedLoginResponse;
import org.codeflow.veterineryproject.auth.repository.AuthReadRepository;
import org.codeflow.veterineryproject.common.api.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UnifiedAuthServiceTest {

    private PlatformAuthService platformAuthService;
    private AuthService authService;
    private AuthReadRepository authReadRepository;
    private UnifiedAuthService service;

    @BeforeEach
    void setUp() {
        platformAuthService = mock(PlatformAuthService.class);
        authService = mock(AuthService.class);
        authReadRepository = mock(AuthReadRepository.class);
        service = new UnifiedAuthService(platformAuthService, authService, authReadRepository);
    }

    private static AuthLoginResponse tenantResponse(UUID org, UUID user, String... roleCodes) {
        List<AuthRoleSummary> roles = java.util.Arrays.stream(roleCodes)
                .map(c -> new AuthRoleSummary(UUID.randomUUID(), c, c)).toList();
        return new AuthLoginResponse("Bearer", "a", "r", 900, org, user, "Ad", List.of(), List.of(), false, roles, List.of());
    }

    @Test
    void platformAdminShouldGetPlatformPanel() {
        PlatformAuthResponse platform = new PlatformAuthResponse("Bearer", "a", "r", 900, UUID.randomUUID(), "Admin", "a@x.com", "PLATFORM_ADMIN");
        when(platformAuthService.login(any(PlatformLoginRequest.class))).thenReturn(platform);

        UnifiedLoginResponse res = service.signIn(new UnifiedLoginRequest("a@x.com", "pw", null));

        assertThat(res.accountType()).isEqualTo("PLATFORM");
        assertThat(res.panel()).isEqualTo(AuthPanel.PLATFORM_ADMIN);
        assertThat(res.platform()).isSameAs(platform);
        assertThat(res.tenant()).isNull();
        verify(authReadRepository, never()).resolveLoginCandidates(any(), any());
    }

    @Test
    void singleTenantCandidateShouldMapRoleToPanel() {
        UUID org = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        when(platformAuthService.login(any())).thenThrow(new BadCredentialsException("x"));
        when(authReadRepository.resolveLoginCandidates("v@x.com", null))
                .thenReturn(List.of(new AuthLoginCandidate(org, 1001, "Org", user, false)));
        when(authService.loginResolved(org, user, "pw")).thenReturn(tenantResponse(org, user, "veterinarian"));

        UnifiedLoginResponse res = service.signIn(new UnifiedLoginRequest("v@x.com", "pw", null));

        assertThat(res.accountType()).isEqualTo("TENANT");
        assertThat(res.panel()).isEqualTo(AuthPanel.VETERINARIAN);
        assertThat(res.availablePanels()).containsExactly(AuthPanel.VETERINARIAN);
        assertThat(res.platform()).isNull();
    }

    @Test
    void clientWithoutRolesShouldGetPetOwnerPanel() {
        UUID org = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        when(platformAuthService.login(any())).thenThrow(new BadCredentialsException("x"));
        when(authReadRepository.resolveLoginCandidates(eq("o@x.com"), isNull()))
                .thenReturn(List.of(new AuthLoginCandidate(org, 1001, "Org", user, true)));
        when(authService.loginResolved(org, user, "pw")).thenReturn(tenantResponse(org, user));

        UnifiedLoginResponse res = service.signIn(new UnifiedLoginRequest("o@x.com", "pw", null));

        assertThat(res.panel()).isEqualTo(AuthPanel.PET_OWNER);
    }

    @Test
    void panelPriorityShouldPreferAdminAndExposeAllPanels() {
        List<AuthRoleSummary> roles = List.of(
                new AuthRoleSummary(UUID.randomUUID(), "receptionist", "R"),
                new AuthRoleSummary(UUID.randomUUID(), "clinic_admin", "A"));

        assertThat(UnifiedAuthService.resolvePanels(roles, true))
                .containsExactly(AuthPanel.CLINIC_ADMIN, AuthPanel.RECEPTION, AuthPanel.PET_OWNER);
        assertThat(UnifiedAuthService.resolvePanels(List.of(), false)).containsExactly(AuthPanel.UNASSIGNED);
    }

    @Test
    void noCandidatesShouldFailWithBadCredentials() {
        when(platformAuthService.login(any())).thenThrow(new BadCredentialsException("x"));
        when(authReadRepository.resolveLoginCandidates(any(), any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.signIn(new UnifiedLoginRequest("n@x.com", "pw", null)))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void multipleMatchesShouldThrowAmbiguousOrganization() {
        AuthLoginCandidate c1 = new AuthLoginCandidate(UUID.randomUUID(), 1, "Org A", UUID.randomUUID(), false);
        AuthLoginCandidate c2 = new AuthLoginCandidate(UUID.randomUUID(), 2, "Org B", UUID.randomUUID(), false);
        when(platformAuthService.login(any())).thenThrow(new BadCredentialsException("x"));
        when(authReadRepository.resolveLoginCandidates(any(), any())).thenReturn(List.of(c1, c2));
        when(authService.credentialsMatch(any(), any(), eq("pw"))).thenReturn(true);

        assertThatThrownBy(() -> service.signIn(new UnifiedLoginRequest("m@x.com", "pw", null)))
                .isInstanceOfSatisfying(ConflictException.class, ex -> {
                    assertThat(ex.code()).isEqualTo("AMBIGUOUS_ORGANIZATION");
                    assertThat(ex.details()).containsKey("organizations");
                });
        verify(authService, never()).loginResolved(any(), any(), any());
    }

    @Test
    void multipleCandidatesWithSingleMatchShouldLoginThatOne() {
        UUID orgB = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        AuthLoginCandidate c1 = new AuthLoginCandidate(UUID.randomUUID(), 1, "Org A", UUID.randomUUID(), false);
        AuthLoginCandidate c2 = new AuthLoginCandidate(orgB, 2, "Org B", userB, false);
        when(platformAuthService.login(any())).thenThrow(new BadCredentialsException("x"));
        when(authReadRepository.resolveLoginCandidates(any(), any())).thenReturn(List.of(c1, c2));
        when(authService.credentialsMatch(c1.organizationId(), c1.userId(), "pw")).thenReturn(false);
        when(authService.credentialsMatch(orgB, userB, "pw")).thenReturn(true);
        when(authService.loginResolved(orgB, userB, "pw")).thenReturn(tenantResponse(orgB, userB, "receptionist"));

        UnifiedLoginResponse res = service.signIn(new UnifiedLoginRequest("m@x.com", "pw", null));

        assertThat(res.panel()).isEqualTo(AuthPanel.RECEPTION);
    }

    @Test
    void organizationCodeShouldSkipPlatformAttempt() {
        when(authReadRepository.resolveLoginCandidates("v@x.com", 1001)).thenReturn(List.of());

        assertThatThrownBy(() -> service.signIn(new UnifiedLoginRequest("v@x.com", "pw", 1001)))
                .isInstanceOf(BadCredentialsException.class);
        verify(platformAuthService, never()).login(any());
    }
}
