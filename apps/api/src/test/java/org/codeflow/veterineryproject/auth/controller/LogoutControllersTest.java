package org.codeflow.veterineryproject.auth.controller;

import org.codeflow.veterineryproject.auth.dto.PlatformAuthResponse;
import org.codeflow.veterineryproject.auth.security.JwtAuthenticatedUser;
import org.codeflow.veterineryproject.auth.service.AuthService;
import org.codeflow.veterineryproject.auth.service.PlatformAuthService;
import org.codeflow.veterineryproject.common.api.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LogoutControllersTest {

    private final AuthService authService = mock(AuthService.class);
    private final PlatformAuthService platformAuthService = mock(PlatformAuthService.class);
    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new AuthController(authService), new PlatformAuthController(platformAuthService))
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    private JwtAuthenticatedUser principal;

    @BeforeEach
    void setUp() {
        UUID clinicId = UUID.randomUUID();
        principal = new JwtAuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), clinicId,
                List.of(clinicId), List.of(), "jti-1", Instant.now().plusSeconds(600));
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void logoutShouldReturn204AndPassRefreshToken() throws Exception {
        mvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"ref\"}"))
                .andExpect(status().isNoContent());

        verify(authService).logout(eq(principal), eq("ref"));
    }

    @Test
    void logoutShouldWorkWithoutBody() throws Exception {
        mvc.perform(post("/api/auth/logout")).andExpect(status().isNoContent());

        verify(authService).logout(eq(principal), isNull());
    }

    @Test
    void platformRefreshShouldReturnNewTokens() throws Exception {
        when(platformAuthService.refresh("old")).thenReturn(new PlatformAuthResponse(
                "Bearer", "acc", "p.new", 900, UUID.randomUUID(), "Admin", "admin@test.com", "PLATFORM_ADMIN"));

        mvc.perform(post("/api/platform/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"old\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("acc"))
                .andExpect(jsonPath("$.refreshToken").value("p.new"));
    }

    @Test
    void platformRefreshShouldValidateBody() throws Exception {
        mvc.perform(post("/api/platform/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void platformRefreshShouldReturn401WhenTokenInvalid() throws Exception {
        when(platformAuthService.refresh(any())).thenThrow(new BadCredentialsException("x"));

        mvc.perform(post("/api/platform/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"bad\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void platformLogoutShouldReturn204WithAndWithoutBody() throws Exception {
        mvc.perform(post("/api/platform/auth/logout").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"p.ref\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/platform/auth/logout")).andExpect(status().isNoContent());

        verify(platformAuthService).logout("p.ref");
        verify(platformAuthService).logout(null);
    }
}
