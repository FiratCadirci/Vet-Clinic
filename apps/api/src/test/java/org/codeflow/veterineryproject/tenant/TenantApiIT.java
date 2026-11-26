package org.codeflow.veterineryproject.tenant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.codeflow.veterineryproject.common.test.PostgresContainerSupport;
import org.codeflow.veterineryproject.common.test.TenantTestData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.codeflow.veterineryproject.common.test.TenantTestData.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Gerçek login + gerçek Flyway şeması + vet_app rolüyle API seviyesinde tenant izolasyonu testleri. */
@SpringBootTest
@AutoConfigureMockMvc
class TenantApiIT extends PostgresContainerSupport {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeAll
    static void seed() {
        TenantTestData.seed(superUserDataSource());
    }

    @Test
    void login_realCredentialsReturnTokenWithOwnBranch() throws Exception {
        JsonNode login = tenantLogin(ORG_A_CODE, EMAIL_A1_S1);

        assertThat(login.get("accessToken").asText()).isNotBlank();
        assertThat(login.get("clinicIds").get(0).asText()).isEqualTo(BRANCH_A1_S1.toString());
    }

    @Test
    void apiRule2_clinicHeaderOfOtherClinic_returns403() throws Exception {
        String token = tenantToken(ORG_A_CODE, EMAIL_A1_S1);

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token)
                        .header("X-Clinic-Id", BRANCH_A2_S1.toString()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/appointments").header("Authorization", "Bearer " + token)
                        .header("X-Clinic-Id", BRANCH_B1_S1.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void apiRule2_clinicHeaderOfOwnBranch_returns200() throws Exception {
        String token = tenantToken(ORG_A_CODE, EMAIL_A1_S1);

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token)
                        .header("X-Clinic-Id", BRANCH_A1_S1.toString()))
                .andExpect(status().isOk());
    }

    @Test
    void apiTokenType_tenantTokenIsRejectedOnPlatformEndpoint() throws Exception {
        String tenantToken = tenantToken(ORG_A_CODE, EMAIL_A1_S1);

        mvc.perform(get("/api/platform/auth/me").header("Authorization", "Bearer " + tenantToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void apiTokenType_platformTokenIsRejectedOnTenantEndpoints() throws Exception {
        String platformToken = platformToken();

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + platformToken))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/appointments").header("Authorization", "Bearer " + platformToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void apiTokenType_platformTokenWorksOnPlatformEndpoint() throws Exception {
        mvc.perform(get("/api/platform/auth/me").header("Authorization", "Bearer " + platformToken()))
                .andExpect(status().isOk());
    }

    @Test
    void apiRule2_getByIdOfOtherClinicAppointment_returns404NotForbidden() throws Exception {
        String token = tenantToken(ORG_A_CODE, EMAIL_A1_S1);

        mvc.perform(get("/api/v1/appointments/" + APPT_A2_S1).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void apiRule3_getByIdOfOtherOrganizationAppointment_returns404NotForbidden() throws Exception {
        String token = tenantToken(ORG_A_CODE, EMAIL_A1_S1);

        mvc.perform(get("/api/v1/appointments/" + APPT_B1_S1).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void apiRule2_nonexistentIdAndOtherTenantIdLookIdentical() throws Exception {
        String token = tenantToken(ORG_A_CODE, EMAIL_A1_S1);

        MvcResult other = mvc.perform(get("/api/v1/appointments/" + APPT_A2_S1).header("Authorization", "Bearer " + token)).andReturn();
        MvcResult missing = mvc.perform(get("/api/v1/appointments/" + UUID.randomUUID()).header("Authorization", "Bearer " + token)).andReturn();

        assertThat(other.getResponse().getStatus()).isEqualTo(missing.getResponse().getStatus()).isEqualTo(404);
        assertThat(objectMapper.readTree(other.getResponse().getContentAsString()).get("code"))
                .isEqualTo(objectMapper.readTree(missing.getResponse().getContentAsString()).get("code"));
    }

    @Test
    void apiRule1_getByIdOfOwnAndSiblingBranchAppointment_returns200() throws Exception {
        String token = tenantToken(ORG_A_CODE, EMAIL_A1_S1);

        mvc.perform(get("/api/v1/appointments/" + APPT_A1_S1).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/appointments/" + APPT_A1_S2).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void apiRule2_3_listContainsOnlyOwnClinicAppointments() throws Exception {
        String token = tenantToken(ORG_A_CODE, EMAIL_A1_S1);

        String body = mvc.perform(get("/api/v1/appointments").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(body).contains(APPT_A1_S1.toString(), APPT_A1_S2.toString());
        assertThat(body).doesNotContain(APPT_A2_S1.toString(), APPT_B1_S1.toString());
    }

    @Test
    void apiRule3_otherOrganizationUserSeesOnlyOwnOrganization() throws Exception {
        String token = tenantToken(ORG_B_CODE, EMAIL_B1_S1);

        String body = mvc.perform(get("/api/v1/appointments").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(body).contains(APPT_B1_S1.toString());
        assertThat(body).doesNotContain(APPT_A1_S1.toString(), APPT_A1_S2.toString(), APPT_A2_S1.toString());
        mvc.perform(get("/api/v1/appointments/" + APPT_A1_S1).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void apiAuth_missingOrInvalidTokenReturns401() throws Exception {
        mvc.perform(get("/api/v1/appointments")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/appointments").header("Authorization", "Bearer garbage")).andExpect(status().isUnauthorized());
    }

    @Test
    void apiMetaRule2_clinicListContainsOnlyOwnClinicBranches() throws Exception {
        String token = tenantToken(ORG_A_CODE, EMAIL_A1_S1);

        String body = mvc.perform(get("/api/v1/clinics").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(body).contains(BRANCH_A1_S1.toString(), BRANCH_A1_S2.toString());
        assertThat(body).doesNotContain(BRANCH_A2_S1.toString(), BRANCH_B1_S1.toString());
    }

    @Test
    void apiMetaRule2_clinicGroupListContainsOnlyOwnClinic() throws Exception {
        String token = tenantToken(ORG_A_CODE, EMAIL_A1_S1);

        String body = mvc.perform(get("/api/v1/clinic-groups").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(body).contains(CLINIC_A1.toString());
        assertThat(body).doesNotContain(CLINIC_A2.toString(), CLINIC_B1.toString());
    }

    @Test
    void apiMetaRule2_otherClinicBranchAndGroupByIdReturn404() throws Exception {
        String token = tenantToken(ORG_A_CODE, EMAIL_A1_S1);

        mvc.perform(get("/api/v1/clinics/" + BRANCH_A2_S1).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/clinic-groups/" + CLINIC_A2).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void apiMetaRule3_otherOrganizationBranchAndGroupByIdReturn404() throws Exception {
        String token = tenantToken(ORG_A_CODE, EMAIL_A1_S1);

        mvc.perform(get("/api/v1/clinics/" + BRANCH_B1_S1).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/clinic-groups/" + CLINIC_B1).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void apiMetaRule1_siblingBranchByIdReturns200() throws Exception {
        String token = tenantToken(ORG_A_CODE, EMAIL_A1_S1);

        mvc.perform(get("/api/v1/clinics/" + BRANCH_A1_S2).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/clinic-groups/" + CLINIC_A1).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void apiMetaFlows_loginAndMeStillWorkAfterClinicMetadataNarrowing() throws Exception {
        String token = tenantToken(ORG_A_CODE, EMAIL_A1_S1);

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token)
                .header("X-Clinic-Id", BRANCH_A1_S1.toString())).andExpect(status().isOk());
    }

    @Test
    void apiMetaFlows_refreshRotatesTokenAndKeepsUserClinics() throws Exception {
        JsonNode login = tenantLogin(ORG_A_CODE, EMAIL_A1_S1);
        String body = objectMapper.writeValueAsString(java.util.Map.of("refreshToken", login.get("refreshToken").asText()));

        MvcResult refreshed = mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();

        JsonNode json = objectMapper.readTree(refreshed.getResponse().getContentAsString());
        assertThat(json.get("clinicIds").get(0).asText()).isEqualTo(BRANCH_A1_S1.toString());
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    private String tenantToken(int orgCode, String email) throws Exception {
        return tenantLogin(orgCode, email).get("accessToken").asText();
    }

    private JsonNode tenantLogin(int orgCode, String email) throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "organizationCode", orgCode, "email", email, "password", PASSWORD));
        MvcResult result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String platformToken() throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of("email", PLATFORM_EMAIL, "password", PASSWORD));
        MvcResult result = mvc.perform(post("/api/platform/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }
}
