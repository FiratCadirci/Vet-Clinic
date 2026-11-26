package org.codeflow.veterineryproject.auth.security;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.codeflow.veterineryproject.auth.service.JwtTokenService;
import org.codeflow.veterineryproject.common.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldRejectWhenClinicHeaderIsNotInTokenClaim() throws Exception {
        JwtTokenService jwtTokenService = mock(JwtTokenService.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtTokenService);

        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID allowedClinic = UUID.randomUUID();
        UUID deniedClinic = UUID.randomUUID();

        when(jwtTokenService.parseAccessToken("token"))
                .thenReturn(new JwtTokenService.AccessTokenPayload(
                        userId,
                        organizationId,
                        List.of(allowedClinic),
                        List.of("clients.read")
                ));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/clients");
        request.addHeader("Authorization", "Bearer token");
        request.addHeader("X-Clinic-Id", deniedClinic.toString());
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("FORBIDDEN");
    }

    @Test
    void shouldUseFirstClinicWhenHeaderIsMissingAndClearContextAfterRequest() throws Exception {
        JwtTokenService jwtTokenService = mock(JwtTokenService.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtTokenService);

        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID firstClinic = UUID.randomUUID();
        UUID secondClinic = UUID.randomUUID();

        when(jwtTokenService.parseAccessToken("token"))
                .thenReturn(new JwtTokenService.AccessTokenPayload(
                        userId,
                        organizationId,
                        List.of(firstClinic, secondClinic),
                        List.of("clients.read")
                ));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/clients");
        request.addHeader("Authorization", "Bearer token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        HttpServlet servlet = new HttpServlet() {
            @Override
            protected void service(HttpServletRequest req, HttpServletResponse res) {
                JwtAuthenticatedUser principal = (JwtAuthenticatedUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
                assertThat(principal.clinicId()).isEqualTo(firstClinic);
                assertThat(TenantContext.get()).isPresent();
                assertThat(TenantContext.get().orElseThrow().clinicId()).isEqualTo(firstClinic);
            }
        };
        MockFilterChain chain = new MockFilterChain(servlet);

        filter.doFilter(request, response, chain);

        assertThat(TenantContext.get()).isEmpty();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void shouldReturn401WhenAuthorizationHeaderMissing() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(mock(JwtTokenService.class));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest("GET", "/api/clients"), response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("UNAUTHORIZED");
    }

    @Test
    void shouldReturn401WhenTokenInvalid() throws Exception {
        JwtTokenService jwtTokenService = mock(JwtTokenService.class);
        when(jwtTokenService.parseAccessToken("bad"))
                .thenThrow(new org.springframework.security.authentication.BadCredentialsException("x"));
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtTokenService);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/clients");
        request.addHeader("Authorization", "Bearer bad");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void shouldReturn403WhenClinicHeaderMalformedOrUserHasNoClinics() throws Exception {
        JwtTokenService jwtTokenService = mock(JwtTokenService.class);
        when(jwtTokenService.parseAccessToken("token"))
                .thenReturn(new JwtTokenService.AccessTokenPayload(
                        UUID.randomUUID(), UUID.randomUUID(), List.of(UUID.randomUUID()), List.of()));
        when(jwtTokenService.parseAccessToken("noclinic"))
                .thenReturn(new JwtTokenService.AccessTokenPayload(
                        UUID.randomUUID(), UUID.randomUUID(), List.of(), List.of()));
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtTokenService);

        MockHttpServletRequest malformed = new MockHttpServletRequest("GET", "/api/clients");
        malformed.addHeader("Authorization", "Bearer token");
        malformed.addHeader("X-Clinic-Id", "not-a-uuid");
        MockHttpServletResponse r1 = new MockHttpServletResponse();
        filter.doFilter(malformed, r1, new MockFilterChain());
        assertThat(r1.getStatus()).isEqualTo(403);

        MockHttpServletRequest none = new MockHttpServletRequest("GET", "/api/clients");
        none.addHeader("Authorization", "Bearer noclinic");
        MockHttpServletResponse r2 = new MockHttpServletResponse();
        filter.doFilter(none, r2, new MockFilterChain());
        assertThat(r2.getStatus()).isEqualTo(403);
    }

    @Test
    void shouldSkipPublicEndpoints() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(mock(JwtTokenService.class));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest("POST", "/api/auth/login"), response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
    }
}