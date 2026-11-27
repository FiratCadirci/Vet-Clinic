package org.codeflow.veterineryproject.clinic;

import com.fasterxml.jackson.databind.JsonNode;
import org.codeflow.veterineryproject.common.test.OrgApiTestSupport;
import org.codeflow.veterineryproject.common.test.OrgTestData;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Şube yönetimi ve FG-K1 aktif şube limiti. */
@SpringBootTest
@AutoConfigureMockMvc
class BranchManagementIT extends OrgApiTestSupport {

    @Test
    void newBranchOpensInSameClinicGroup_creatorIsAssigned_andActiveBranchLimitIsEnforced() throws Exception {
        Session admin = signup(OrgTestData.createPlan(superUserDataSource(), 2, 10));
        String groupId = body(getJson(admin.token(), "/api/v1/clinics/" + admin.clinicId())).get("data").get("clinicGroupId").asText();

        JsonNode created = body(postJson(admin.token(), admin.clinic(), "/api/v1/clinics",
                Map.of("name", "Beşiktaş Şubesi", "phone", "02125552020", "addressLine", "Ihlamurdere Cad. No:5"))
                .andExpect(status().isCreated()));
        String branchId = created.get("id").asText();
        assertThat(created.get("code").asText()).startsWith("BESIKTAS-SUBESI-");
        assertThat(created.get("isActive").asBoolean()).isTrue();
        assertThat(created.get("clinicGroupId").asText()).isEqualTo(groupId);
        assertThat(OrgTestData.count(superUserDataSource(), """
                SELECT count(*) FROM vet.user_clinic_roles ucr JOIN vet.roles r ON r.id = ucr.role_id
                WHERE ucr.user_id = ? AND ucr.clinic_id = ?::uuid AND r.code = 'clinic_admin'
                """, admin.userId(), branchId)).isEqualTo(1);
        assertThat(relogin(admin).body().get("clinicIds").toString()).contains(branchId);

        postJson(admin.token(), admin.clinic(), "/api/v1/clinics", Map.of("name", "Üsküdar Şubesi"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PLAN_BRANCH_LIMIT"))
                .andExpect(jsonPath("$.details.limit").value(2));

        // Pasif şube limitte sayılmaz; yer dolunca pasif şube yeniden açılamaz.
        postJson(admin.token(), admin.clinic(), "/api/v1/clinics/" + branchId + "/deactivate", Map.of())
                .andExpect(status().isOk()).andExpect(jsonPath("$.isActive").value(false));
        postJson(admin.token(), admin.clinic(), "/api/v1/clinics", Map.of("name", "Üsküdar Şubesi")).andExpect(status().isCreated());
        postJson(admin.token(), admin.clinic(), "/api/v1/clinics/" + branchId + "/activate", Map.of())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PLAN_BRANCH_LIMIT"));

        putJson(admin.token(), admin.clinic(), "/api/v1/clinics/" + branchId, Map.of("name", "Beşiktaş Merkez", "city", "İstanbul"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Beşiktaş Merkez"))
                .andExpect(jsonPath("$.city").value("İstanbul"));
    }

    @Test
    void branchManagementNeedsPermission_otherOrganizationIs404_validationIs400() throws Exception {
        Session admin = signup(OrgTestData.createPlan(superUserDataSource(), 3, 10));
        Session manager = staff(admin, "branch_manager", admin.clinicId());
        Session other = signup(null);

        postJson(manager.token(), manager.clinic(), "/api/v1/clinics", Map.of("name", "Yetkisiz Şube")).andExpect(status().isForbidden());
        postJson(manager.token(), manager.clinic(), "/api/v1/clinics/" + admin.clinicId() + "/deactivate", Map.of())
                .andExpect(status().isForbidden());

        putJson(other.token(), other.clinic(), "/api/v1/clinics/" + admin.clinicId(), Map.of("name", "Ele geçir"))
                .andExpect(status().isNotFound());
        postJson(other.token(), other.clinic(), "/api/v1/clinics/" + admin.clinicId() + "/deactivate", Map.of())
                .andExpect(status().isNotFound());

        postJson(admin.token(), admin.clinic(), "/api/v1/clinics", Map.of("name", "")).andExpect(status().isBadRequest());
        postJson(admin.token(), null, "/api/v1/clinics", Map.of("name", "Başlıksız")).andExpect(status().isBadRequest());
    }
}
