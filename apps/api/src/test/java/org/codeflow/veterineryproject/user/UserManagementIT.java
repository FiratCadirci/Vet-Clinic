package org.codeflow.veterineryproject.user;

import com.fasterxml.jackson.databind.JsonNode;
import org.codeflow.veterineryproject.common.test.ApiTestSupport;
import org.codeflow.veterineryproject.common.test.RegistrationTestData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.codeflow.veterineryproject.common.test.RegistrationTestData.*;
import static org.codeflow.veterineryproject.common.test.TenantTestData.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UserManagementIT extends ApiTestSupport {

    private static final String INITIAL_PASSWORD = "Gecici-Sifre-1";

    @BeforeAll
    static void seed() {
        RegistrationTestData.seed(superUserDataSource());
    }

    @Test
    void create_returnsUserWithMustChangePasswordAndAssignment() throws Exception {
        String token = token(ORG_A_CODE, EMAIL_MANAGER_A1);

        JsonNode created = body(postJson(token, "/api/v1/users", newUser("create-ok@test.com", BRANCH_A1_S2, ROLE_READER_A))
                .andExpect(status().isCreated()));

        assertThat(created.get("email").asText()).isEqualTo("create-ok@test.com");
        assertThat(created.get("mustChangePassword").asBoolean()).isTrue();
        assertThat(created.get("active").asBoolean()).isTrue();
        assertThat(created.get("clinicRoles")).hasSize(1);
        assertThat(created.get("clinicRoles").get(0).get("clinicId").asText()).isEqualTo(BRANCH_A1_S2.toString());
        assertThat(created.toString()).doesNotContain("password");
    }

    @Test
    void create_duplicateEmailInOrganization_returns409() throws Exception {
        String token = token(ORG_A_CODE, EMAIL_MANAGER_A1);
        postJson(token, "/api/v1/users", newUser("dup@test.com", BRANCH_A1_S1, ROLE_READER_A)).andExpect(status().isCreated());

        postJson(token, "/api/v1/users", newUser("DUP@test.com", BRANCH_A1_S2, ROLE_READER_A)).andExpect(status().isConflict());
        postJson(token, "/api/v1/users", newUser(EMAIL_MANAGER_A2, BRANCH_A1_S2, ROLE_READER_A)).andExpect(status().isConflict());
    }

    @Test
    void create_sameEmailInOtherOrganizationIsAllowed() throws Exception {
        String tokenB = token(ORG_B_CODE, EMAIL_MANAGER_B1);

        postJson(tokenB, "/api/v1/users", newUser(EMAIL_MANAGER_A1, BRANCH_B1_S1, ROLE_MANAGER_B)).andExpect(status().isCreated());
    }

    @Test
    void create_withoutAssignmentOrWithWeakPassword_returns400() throws Exception {
        String token = token(ORG_A_CODE, EMAIL_MANAGER_A1);

        postJson(token, "/api/v1/users", Map.of("email", "noassign@test.com", "displayName", "X",
                "initialPassword", INITIAL_PASSWORD, "assignments", List.of())).andExpect(status().isBadRequest());
        Map<String, Object> weak = new java.util.HashMap<>(newUser("weak@test.com", BRANCH_A1_S1, ROLE_READER_A));
        weak.put("initialPassword", "short");
        postJson(token, "/api/v1/users", weak).andExpect(status().isBadRequest());
    }

    @Test
    void create_branchOutsideCallerClinicGroupOrOrganization_returns422AndLeavesNoUser() throws Exception {
        String token = token(ORG_A_CODE, EMAIL_MANAGER_A1);

        postJson(token, "/api/v1/users", newUser("other-group@test.com", BRANCH_A2_S1, ROLE_READER_A)).andExpect(status().isUnprocessableEntity());
        postJson(token, "/api/v1/users", newUser("other-org@test.com", BRANCH_B1_S1, ROLE_READER_A)).andExpect(status().isUnprocessableEntity());
        postJson(token, "/api/v1/users", newUser("other-role@test.com", BRANCH_A1_S1, ROLE_MANAGER_B)).andExpect(status().isUnprocessableEntity());

        postJson(token, "/api/v1/users", newUser("other-group@test.com", BRANCH_A1_S1, ROLE_READER_A)).andExpect(status().isCreated());
    }

    @Test
    void create_withoutUsersManagePermission_returns403() throws Exception {
        postJson(token(ORG_A_CODE, EMAIL_LIMITED_A1), "/api/v1/users", newUser("forbidden@test.com", BRANCH_A1_S1, ROLE_READER_A))
                .andExpect(status().isForbidden());
        getJson(token(ORG_A_CODE, EMAIL_READER_A1), "/api/v1/users").andExpect(status().isForbidden());
        getJson(token(ORG_A_CODE, EMAIL_LIMITED_A1), "/api/v1/roles").andExpect(status().isForbidden());
    }

    @Test
    void firstLogin_forcesPasswordChangeThenAllowsNormalUse() throws Exception {
        String managerToken = token(ORG_A_CODE, EMAIL_MANAGER_A1);
        postJson(managerToken, "/api/v1/users", newUser("first-login@test.com", BRANCH_A1_S1, ROLE_MANAGER_A)).andExpect(status().isCreated());

        JsonNode login = login(ORG_A_CODE, "first-login@test.com", INITIAL_PASSWORD);
        String token = login.get("accessToken").asText();
        String refresh = login.get("refreshToken").asText();
        assertThat(login.get("mustChangePassword").asBoolean()).isTrue();

        getJson(token, "/api/v1/users").andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
        getJson(token, "/api/v1/clients").andExpect(status().isForbidden());
        getJson(token, "/api/auth/me").andExpect(status().isOk()).andExpect(jsonPath("$.mustChangePassword").value(true));

        postJson(token, "/api/auth/change-password", Map.of("currentPassword", "yanlis-sifre-123", "newPassword", "Yeni-Sifre-2026"))
                .andExpect(status().isUnauthorized());
        postJson(token, "/api/auth/change-password", Map.of("currentPassword", INITIAL_PASSWORD, "newPassword", INITIAL_PASSWORD))
                .andExpect(status().isBadRequest());
        postJson(token, "/api/auth/change-password", Map.of("currentPassword", INITIAL_PASSWORD, "newPassword", "kisa"))
                .andExpect(status().isBadRequest());
        postJson(token, "/api/auth/change-password", Map.of("currentPassword", INITIAL_PASSWORD, "newPassword", "Yeni-Sifre-2026"))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", refresh))))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("organizationCode", ORG_A_CODE, "email", "first-login@test.com", "password", INITIAL_PASSWORD))))
                .andExpect(status().isUnauthorized());

        JsonNode relogin = login(ORG_A_CODE, "first-login@test.com", "Yeni-Sifre-2026");
        assertThat(relogin.get("mustChangePassword").asBoolean()).isFalse();
        getJson(relogin.get("accessToken").asText(), "/api/v1/users").andExpect(status().isOk());
    }

    @Test
    void visibility_siblingBranchUserVisible_otherGroupAndOrganizationReturn404() throws Exception {
        String token = token(ORG_A_CODE, EMAIL_MANAGER_A1);

        getJson(token, "/api/v1/users/" + READER_A1).andExpect(status().isOk());
        getJson(token, "/api/v1/users/" + MANAGER_A1).andExpect(status().isOk());
        getJson(token, "/api/v1/users/" + MANAGER_A2).andExpect(status().isNotFound());
        getJson(token, "/api/v1/users/" + MANAGER_B1).andExpect(status().isNotFound());
        getJson(token, "/api/v1/users/" + UUID.randomUUID()).andExpect(status().isNotFound());
        postJson(token, "/api/v1/users/" + MANAGER_A2 + "/deactivate", Map.of()).andExpect(status().isNotFound());
        patchJson(token, "/api/v1/users/" + MANAGER_B1, Map.of("displayName", "Hacked")).andExpect(status().isNotFound());
    }

    @Test
    void list_containsOnlySameClinicGroupUsers() throws Exception {
        String token = token(ORG_A_CODE, EMAIL_MANAGER_A1);

        String body = getJson(token, "/api/v1/users?size=200").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(body).contains(MANAGER_A1.toString(), READER_A1.toString(), USER_A1_S2.toString());
        assertThat(body).doesNotContain(MANAGER_A2.toString(), MANAGER_B1.toString(), USER_A2_S1.toString(), USER_B1_S1.toString());
        getJson(token, "/api/v1/users?q=reader-a1").andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void update_changesNameAndPhoneOnly() throws Exception {
        String token = token(ORG_A_CODE, EMAIL_MANAGER_A1);
        JsonNode created = body(postJson(token, "/api/v1/users", newUser("patch-me@test.com", BRANCH_A1_S1, ROLE_READER_A)));

        JsonNode updated = body(patchJson(token, "/api/v1/users/" + created.get("id").asText(),
                Map.of("displayName", "Yeni Ad", "phone", "+905551112233")).andExpect(status().isOk()));

        assertThat(updated.get("displayName").asText()).isEqualTo("Yeni Ad");
        assertThat(updated.get("phone").asText()).isEqualTo("+905551112233");
        assertThat(updated.get("email").asText()).isEqualTo("patch-me@test.com");
    }

    @Test
    void clinicRoles_addAndEnd_keepsHistoryAndRequiresAtLeastOneActiveRole() throws Exception {
        String token = token(ORG_A_CODE, EMAIL_MANAGER_A1);
        JsonNode created = body(postJson(token, "/api/v1/users", newUser("roles@test.com", BRANCH_A1_S1, ROLE_READER_A)));
        String id = created.get("id").asText();
        String firstAssignment = created.get("clinicRoles").get(0).get("id").asText();

        delete(token, "/api/v1/users/" + id + "/clinic-roles/" + firstAssignment).andExpect(status().isUnprocessableEntity());

        postJson(token, "/api/v1/users/" + id + "/clinic-roles",
                Map.of("clinicId", BRANCH_A1_S2, "roleId", ROLE_LIMITED_A)).andExpect(status().isCreated());
        postJson(token, "/api/v1/users/" + id + "/clinic-roles",
                Map.of("clinicId", BRANCH_A1_S2, "roleId", ROLE_LIMITED_A)).andExpect(status().isConflict());
        postJson(token, "/api/v1/users/" + id + "/clinic-roles",
                Map.of("clinicId", BRANCH_A2_S1, "roleId", ROLE_LIMITED_A)).andExpect(status().isUnprocessableEntity());

        JsonNode afterEnd = body(delete(token, "/api/v1/users/" + id + "/clinic-roles/" + firstAssignment).andExpect(status().isOk()));
        assertThat(afterEnd.get("clinicRoles")).hasSize(2);
        assertThat(afterEnd.get("clinicRoles").get(0).get("validTo").isNull()).isFalse();
        delete(token, "/api/v1/users/" + id + "/clinic-roles/" + UUID.randomUUID()).andExpect(status().isNotFound());
    }

    @Test
    void deactivate_blocksLoginAndRefresh_activateRestoresLogin() throws Exception {
        String token = token(ORG_A_CODE, EMAIL_MANAGER_A1);
        JsonNode created = body(postJson(token, "/api/v1/users", newUser("deact@test.com", BRANCH_A1_S1, ROLE_READER_A)));
        String id = created.get("id").asText();
        String refresh = login(ORG_A_CODE, "deact@test.com", INITIAL_PASSWORD).get("refreshToken").asText();

        JsonNode deactivated = body(postJson(token, "/api/v1/users/" + id + "/deactivate", Map.of()).andExpect(status().isOk()));
        assertThat(deactivated.get("active").asBoolean()).isFalse();

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("organizationCode", ORG_A_CODE, "email", "deact@test.com", "password", INITIAL_PASSWORD))))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", refresh))))
                .andExpect(status().isUnauthorized());

        postJson(token, "/api/v1/users/" + id + "/activate", Map.of()).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));
        login(ORG_A_CODE, "deact@test.com", INITIAL_PASSWORD);
    }

    @Test
    void deactivate_selfIsRejected() throws Exception {
        postJson(token(ORG_A_CODE, EMAIL_MANAGER_A1), "/api/v1/users/" + MANAGER_A1 + "/deactivate", Map.of())
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void deactivate_lastActiveManagerOfClinicGroupIsRejected() throws Exception {
        String token = token(ORG_A_CODE, EMAIL_DUAL_A);

        postJson(token, "/api/v1/users/" + MANAGER_A2 + "/deactivate", Map.of()).andExpect(status().isUnprocessableEntity());
        getJson(token, "/api/v1/users/" + MANAGER_A2).andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void roles_listsOnlyOwnOrganizationRolesWithPermissionCodes() throws Exception {
        String body = getJson(token(ORG_A_CODE, EMAIL_MANAGER_A1), "/api/v1/roles").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains(ROLE_MANAGER_A.toString(), ROLE_READER_A.toString(), "users.manage", "clients.read");
        assertThat(body).doesNotContain(ROLE_MANAGER_B.toString());
    }

    private Map<String, Object> newUser(String email, UUID clinicId, UUID roleId) {
        return Map.of(
                "email", email,
                "displayName", "Test Kullanıcı",
                "phone", "05550000000",
                "initialPassword", INITIAL_PASSWORD,
                "assignments", List.of(Map.of("clinicId", clinicId, "roleId", roleId))
        );
    }
}
