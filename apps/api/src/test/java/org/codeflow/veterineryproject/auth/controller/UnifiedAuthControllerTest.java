package org.codeflow.veterineryproject.auth.controller;

import org.codeflow.veterineryproject.auth.dto.AuthPanel;
import org.codeflow.veterineryproject.auth.dto.UnifiedLoginRequest;
import org.codeflow.veterineryproject.auth.dto.UnifiedLoginResponse;
import org.codeflow.veterineryproject.auth.service.UnifiedAuthService;
import org.codeflow.veterineryproject.common.api.ConflictException;
import org.codeflow.veterineryproject.common.api.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UnifiedAuthControllerTest {

    private final UnifiedAuthService service = mock(UnifiedAuthService.class);
    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new UnifiedAuthController(service))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    private static final String BODY = "{\"email\":\"v@x.com\",\"password\":\"pw\"}";

    @Test
    void organizationsShouldReturnList() throws Exception {
        when(service.listOrganizations()).thenReturn(List.of(
                new org.codeflow.veterineryproject.auth.dto.OrganizationOption(1, "Org A")));

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/auth/organizations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value(1))
                .andExpect(jsonPath("$[0].name").value("Org A"));
    }

    @Test
    void signInShouldReturnPanel() throws Exception {
        when(service.signIn(any(UnifiedLoginRequest.class))).thenReturn(new UnifiedLoginResponse(
                "TENANT", AuthPanel.RECEPTION, List.of(AuthPanel.RECEPTION), null, null));

        mvc.perform(post("/api/auth/sign-in").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountType").value("TENANT"))
                .andExpect(jsonPath("$.panel").value("RECEPTION"));
    }

    @Test
    void invalidBodyShouldReturn400() throws Exception {
        mvc.perform(post("/api/auth/sign-in").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-mail\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void badCredentialsShouldReturn401() throws Exception {
        when(service.signIn(any())).thenThrow(new BadCredentialsException("x"));

        mvc.perform(post("/api/auth/sign-in").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void ambiguousOrganizationShouldReturn409WithOrganizations() throws Exception {
        when(service.signIn(any())).thenThrow(new ConflictException("msg", "AMBIGUOUS_ORGANIZATION",
                Map.of("organizations", List.of(Map.of("code", 1, "name", "Org A")))));

        mvc.perform(post("/api/auth/sign-in").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AMBIGUOUS_ORGANIZATION"))
                .andExpect(jsonPath("$.details.organizations[0].code").value(1));
    }
}
