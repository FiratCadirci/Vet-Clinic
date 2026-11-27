package org.codeflow.veterineryproject.common.test;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterAll;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.codeflow.veterineryproject.common.test.OrgTestData.PASSWORD;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Kayıt (signup) ile açılan organizasyonlar üzerinde çalışan IT'lerin ortak yardımcıları. */
public abstract class OrgApiTestSupport extends ApiTestSupport {

    /** Oturum açmış personel: organizasyon, kullanıcı, ilk şube ve access token. */
    public record Session(UUID organizationId, int organizationCode, UUID userId, UUID clinicId, String email, String token,
                          JsonNode body) {
        public String clinic() {
            return clinicId.toString();
        }
    }

    @AfterAll
    static void cleanUpOrganizations() {
        OrgTestData.cleanup(superUserDataSource());
    }

    protected static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "@itorg.test";
    }

    protected Session signup(String planCode) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("clinicName", OrgTestData.ORG_PREFIX + "Pati Çiğdem Kliniği " + UUID.randomUUID().toString().substring(0, 6));
        body.put("adminFullName", "Çiğdem Öztürk");
        body.put("adminEmail", uniqueEmail("kurucu"));
        body.put("password", PASSWORD);
        body.put("branchName", "Kadıköy Şubesi");
        if (planCode != null) {
            body.put("planCode", planCode);
        }
        MvcResult result = mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))).andExpect(status().isCreated()).andReturn();
        return session(objectMapper.readTree(result.getResponse().getContentAsString()), (String) body.get("adminEmail"));
    }

    /** Daveti DB'ye bilinen token ile yazar ve API üzerinden kabul eder; davetli kullanıcının oturumunu döner. */
    protected Session staff(Session admin, String roleCode, UUID... clinicIds) throws Exception {
        String email = uniqueEmail(roleCode);
        String token = OrgTestData.insertInvitation(superUserDataSource(), admin.organizationId(), email, roleCode,
                admin.userId(), List.of(clinicIds));
        return accept(token, email);
    }

    protected Session accept(String token, String email) throws Exception {
        MvcResult result = acceptRaw(token).andExpect(status().isCreated()).andReturn();
        return session(objectMapper.readTree(result.getResponse().getContentAsString()), email);
    }

    protected ResultActions acceptRaw(String token) throws Exception {
        return mvc.perform(post("/api/auth/accept-invite").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("token", token, "fullName", "Yeni Personel", "password", PASSWORD))));
    }

    /** Yeni şube atamaları token'a girişte yansır; yeniden giriş yapılır. */
    protected Session relogin(Session session) throws Exception {
        return session(login(session.organizationCode(), session.email(), PASSWORD), session.email());
    }

    protected ResultActions putJson(String token, String clinicId, String url, Object body) throws Exception {
        return mvc.perform(auth(put(url), token, clinicId).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    protected ResultActions deleteJson(String token, String clinicId, String url) throws Exception {
        return mvc.perform(auth(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(url), token, clinicId));
    }

    protected UUID makeVeterinarian(Session admin, UUID userId) throws Exception {
        JsonNode vet = body(postJson(admin.token(), "/api/v1/users/" + userId + "/veterinarian",
                Map.of("licenseNo", "LIC-" + UUID.randomUUID().toString().substring(0, 8))).andExpect(status().isCreated()));
        return UUID.fromString(vet.get("id").asText());
    }

    private Session session(JsonNode body, String email) {
        UUID organizationId = UUID.fromString(body.get("organizationId").asText());
        return new Session(organizationId, OrgTestData.organizationCode(superUserDataSource(), organizationId),
                UUID.fromString(body.get("userId").asText()), UUID.fromString(body.get("clinicIds").get(0).asText()),
                email, body.get("accessToken").asText(), body);
    }

    private static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder builder, String token, String clinicId) {
        builder.header("Authorization", "Bearer " + token);
        if (clinicId != null) {
            builder.header("X-Clinic-Id", clinicId);
        }
        return builder;
    }
}
