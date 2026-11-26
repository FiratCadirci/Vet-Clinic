package org.codeflow.veterineryproject.auth.controller;

import org.codeflow.veterineryproject.auth.dto.AuthLoginResponse;
import org.codeflow.veterineryproject.auth.dto.PlatformAuthResponse;
import org.codeflow.veterineryproject.auth.service.AuthService;
import org.codeflow.veterineryproject.auth.service.PlatformAuthService;
import org.codeflow.veterineryproject.common.api.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllersTest {

    private final AuthService authService = mock(AuthService.class);
    private final PlatformAuthService platformAuthService = mock(PlatformAuthService.class);
    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new AuthController(authService), new PlatformAuthController(platformAuthService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void loginShouldReturnTokens() throws Exception {
        when(authService.login(any())).thenReturn(new AuthLoginResponse(
                "Bearer", "acc", "ref", 900, UUID.randomUUID(), UUID.randomUUID(), "Vet", List.of(), List.of()));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationCode\":1001,\"email\":\"vet@test.com\",\"password\":\"secret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("acc"))
                .andExpect(jsonPath("$.refreshToken").value("ref"));
    }

    @Test
    void loginShouldReturn400WhenBodyInvalid() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationCode\":null,\"email\":\"not-mail\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginShouldReturn401WhenCredentialsWrong() throws Exception {
        when(authService.login(any())).thenThrow(new BadCredentialsException("x"));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationCode\":1001,\"email\":\"vet@test.com\",\"password\":\"bad\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshShouldValidateBody() throws Exception {
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{\"refreshToken\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void platformLoginShouldReturnToken() throws Exception {
        when(platformAuthService.login(any())).thenReturn(new PlatformAuthResponse(
                "Bearer", "plat", 900, UUID.randomUUID(), "Admin", "admin@test.com", "PLATFORM_ADMIN"));

        mvc.perform(post("/api/platform/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@test.com\",\"password\":\"secret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("plat"));
    }

    @Test
    void platformMeShouldRequireBearerHeader() throws Exception {
        mvc.perform(get("/api/platform/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void platformMeShouldReturnAdmin() throws Exception {
        when(platformAuthService.me("plat")).thenReturn(new PlatformAuthResponse(
                "Bearer", "plat", 900, UUID.randomUUID(), "Admin", "admin@test.com", "PLATFORM_ADMIN"));

        mvc.perform(get("/api/platform/auth/me").header("Authorization", "Bearer plat"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("admin@test.com"));
    }
}
