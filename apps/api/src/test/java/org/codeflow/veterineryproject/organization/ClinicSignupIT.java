package org.codeflow.veterineryproject.organization;

import com.fasterxml.jackson.databind.JsonNode;
import org.codeflow.veterineryproject.common.test.OrgApiTestSupport;
import org.codeflow.veterineryproject.common.test.OrgTestData;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** FG-A1 klinik kaydı ve klinik profili. */
@SpringBootTest
@AutoConfigureMockMvc
class ClinicSignupIT extends OrgApiTestSupport {

    @Test
    void signup_createsOrganizationBranchAdminRolesAndTrialInOneStep_andReturnsSession() throws Exception {
        String plan = OrgTestData.createPlan(superUserDataSource(), 2, 3);
        Session admin = signup(plan);

        JsonNode body = admin.body();
        assertThat(body.get("accessToken").asText()).isNotBlank();
        assertThat(body.get("refreshToken").asText()).isNotBlank();
        assertThat(body.get("mustChangePassword").asBoolean()).isFalse();
        assertThat(body.get("roles").get(0).get("code").asText()).isEqualTo("clinic_admin");
        assertThat(body.get("permissions").toString()).contains("branches.manage", "users.manage", "subscriptions.manage");
        assertThat(body.get("clinics").get(0).get("name").asText()).isEqualTo("Kadıköy Şubesi");

        var su = superUserDataSource();
        var org = admin.organizationId();
        assertThat(admin.organizationCode()).isGreaterThanOrEqualTo(1001);
        assertThat(OrgTestData.count(su, "SELECT count(*) FROM vet.clinic_groups WHERE organization_id = ?", org)).isEqualTo(1);
        assertThat(OrgTestData.count(su, "SELECT count(*) FROM vet.clinics WHERE organization_id = ?", org)).isEqualTo(1);
        assertThat(OrgTestData.count(su, "SELECT count(*) FROM vet.roles WHERE organization_id = ? AND is_system_role", org)).isEqualTo(4);
        assertThat(OrgTestData.count(su, "SELECT count(*) FROM vet.vaccine_protocols WHERE organization_id = ? AND status = 'DRAFT'", org))
                .isEqualTo(4);
        assertThat(OrgTestData.count(su, """
                SELECT count(*) FROM vet.organization_subscriptions s JOIN vet.subscription_plans p ON p.id = s.plan_id
                WHERE s.organization_id = ? AND s.status = 'TRIAL' AND p.code = ?
                  AND s.current_period_end - s.current_period_start = interval '14 days'
                  AND s.max_branches_snapshot = 2 AND s.max_users_snapshot = 3
                """, org, plan)).isEqualTo(1);
        assertThat(OrgTestData.count(su, "SELECT count(*) FROM vet.audit_logs WHERE organization_id = ? AND action = 'ORGANIZATION_SIGNUP'", org))
                .isEqualTo(1);

        // Dönen oturum gerçekten çalışır; profil deneme aboneliğini ve kullanımı gösterir.
        getJson(admin.token(), "/api/v1/organization").andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(org.toString()))
                .andExpect(jsonPath("$.code").value(admin.organizationCode()))
                .andExpect(jsonPath("$.subscription.status").value("TRIAL"))
                .andExpect(jsonPath("$.subscription.maxBranches").value(2))
                .andExpect(jsonPath("$.usage.activeBranches").value(1))
                .andExpect(jsonPath("$.usage.activeUsers").value(1));
    }

    @Test
    void signup_withoutPlanCode_usesCheapestActiveMonthlyPlan() throws Exception {
        Session admin = signup(null);

        assertThat(OrgTestData.count(superUserDataSource(), """
                SELECT count(*) FROM vet.organization_subscriptions s JOIN vet.subscription_plans p ON p.id = s.plan_id
                WHERE s.organization_id = ? AND s.status = 'TRIAL' AND p.billing_period = 'MONTHLY'
                """, admin.organizationId())).isEqualTo(1);
    }

    @Test
    void signup_unknownPlanIs400AndCreatesNothing_invalidBodyIs400WithTurkishFieldErrors() throws Exception {
        long before = OrgTestData.count(superUserDataSource(), "SELECT count(*) FROM vet.organizations");
        Map<String, Object> request = new HashMap<>(Map.of("clinicName", OrgTestData.ORG_PREFIX + "Yok", "adminFullName", "Ad Soyad",
                "adminEmail", uniqueEmail("yok"), "password", OrgTestData.PASSWORD, "planCode", "olmayan-paket"));
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        assertThat(OrgTestData.count(superUserDataSource(), "SELECT count(*) FROM vet.organizations")).isEqualTo(before);

        request.put("planCode", null);
        request.put("password", "kisa");
        request.put("adminEmail", "gecersiz");
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.fields.password").exists())
                .andExpect(jsonPath("$.details.fields.adminEmail").exists());
    }

    @Test
    void signupPlans_arePublic() throws Exception {
        String plan = OrgTestData.createPlan(superUserDataSource(), 1, 2);
        JsonNode plans = objectMapper.readTree(mvc.perform(get("/api/auth/signup/plans")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(plans.toString()).contains(plan);
    }

    @Test
    void organizationProfile_updateNeedsBranchesManage_andOtherOrganizationIsUntouched() throws Exception {
        Session admin = signup(OrgTestData.createPlan(superUserDataSource(), 2, 5));
        Session other = signup(null);
        Session reception = staff(admin, "receptionist", admin.clinicId());
        Map<String, Object> profile = new HashMap<>();
        profile.put("name", OrgTestData.ORG_PREFIX + "Yeni Ad");
        profile.put("phone", "02165551010");
        profile.put("email", "INFO@itorg.test");
        profile.put("addressLine", "Moda Cad. No:12 Kadıköy");
        profile.put("taxNumber", "7310045821");

        putJson(reception.token(), reception.clinic(), "/api/v1/organization", profile).andExpect(status().isForbidden());
        getJson(reception.token(), "/api/v1/organization").andExpect(status().isOk());

        putJson(admin.token(), admin.clinic(), "/api/v1/organization", profile).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(OrgTestData.ORG_PREFIX + "Yeni Ad"))
                .andExpect(jsonPath("$.email").value("info@itorg.test"))
                .andExpect(jsonPath("$.addressLine").value("Moda Cad. No:12 Kadıköy"));
        getJson(other.token(), "/api/v1/organization").andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(other.organizationId().toString()));

        profile.put("name", "");
        putJson(admin.token(), admin.clinic(), "/api/v1/organization", profile).andExpect(status().isBadRequest());
        putJson(admin.token(), null, "/api/v1/organization", Map.of("name", "X Y")).andExpect(status().isBadRequest());
    }
}
