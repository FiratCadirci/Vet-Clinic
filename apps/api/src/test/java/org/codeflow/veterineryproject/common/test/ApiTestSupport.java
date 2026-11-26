package org.codeflow.veterineryproject.common.test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;

import static org.codeflow.veterineryproject.common.test.TenantTestData.PASSWORD;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Gerçek login ile token alıp MockMvc üzerinden JSON çağrıları yapan IT yardımcıları. */
public abstract class ApiTestSupport extends PostgresContainerSupport {

    @org.junit.jupiter.api.AfterAll
    static void cleanUpRegistrationData() {
        RegistrationTestData.cleanup(superUserDataSource());
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper objectMapper;

    protected JsonNode login(int orgCode, String email, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("organizationCode", orgCode, "email", email, "password", password))))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    protected String token(int orgCode, String email) throws Exception {
        return login(orgCode, email, PASSWORD).get("accessToken").asText();
    }

    protected ResultActions getJson(String token, String url) throws Exception {
        return mvc.perform(withAuth(get(url), token, null));
    }

    protected ResultActions getJson(String token, String clinicId, String url) throws Exception {
        return mvc.perform(withAuth(get(url), token, clinicId));
    }

    protected ResultActions postJson(String token, String url, Object body) throws Exception {
        return mvc.perform(withAuth(post(url), token, null).contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    protected ResultActions postJson(String token, String clinicId, String url, Object body) throws Exception {
        return mvc.perform(withAuth(post(url), token, clinicId).contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    protected ResultActions patchJson(String token, String url, Object body) throws Exception {
        return mvc.perform(withAuth(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(url), token, null)
                .contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    protected ResultActions delete(String token, String url) throws Exception {
        return mvc.perform(withAuth(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(url), token, null));
    }

    protected JsonNode body(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
    }

    private MockHttpServletRequestBuilder withAuth(MockHttpServletRequestBuilder builder, String token, String clinicId) {
        builder.header("Authorization", "Bearer " + token);
        if (clinicId != null) {
            builder.header("X-Clinic-Id", clinicId);
        }
        return builder;
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }
}
