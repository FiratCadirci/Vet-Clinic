package org.codeflow.veterineryproject.user;

import com.fasterxml.jackson.databind.JsonNode;
import org.codeflow.veterineryproject.common.test.OrgApiTestSupport;
import org.codeflow.veterineryproject.common.test.OrgTestData;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** FG-A5 (yönetici rolü verme, son klinik yöneticisi), FG-A4 (şube yöneticisinin kapsamı), FG-J3 (yetki matrisi). */
@SpringBootTest
@AutoConfigureMockMvc
class StaffManagementIT extends OrgApiTestSupport {

    private UUID role(Session s, String code) {
        return OrgTestData.roleId(superUserDataSource(), s.organizationId(), code);
    }

    private UUID createBranch(Session admin, String name) throws Exception {
        return UUID.fromString(body(postJson(admin.token(), admin.clinic(), "/api/v1/clinics", Map.of("name", name))
                .andExpect(status().isCreated())).get("id").asText());
    }

    @Test
    void branchManagerCannotGrantClinicAdminOrTouchClinicAdmins_butManagesOwnBranchStaff() throws Exception {
        Session admin = signup(OrgTestData.createPlan(superUserDataSource(), 3, 10));
        Session manager = staff(admin, "branch_manager", admin.clinicId());
        Session reception = staff(admin, "receptionist", admin.clinicId());
        String users = "/api/v1/users/";

        putJson(manager.token(), null, users + reception.userId() + "/clinic-roles",
                Map.of("roleId", role(admin, "clinic_admin"), "clinicIds", List.of(admin.clinicId()))).andExpect(status().isForbidden());
        postJson(manager.token(), users + reception.userId() + "/clinic-roles",
                Map.of("clinicId", admin.clinicId(), "roleId", role(admin, "clinic_admin"))).andExpect(status().isForbidden());
        postJson(manager.token(), users + admin.userId() + "/deactivate", Map.of()).andExpect(status().isForbidden());
        putJson(manager.token(), null, users + admin.userId() + "/clinic-roles",
                Map.of("roleId", role(admin, "veterinarian"), "clinicIds", List.of(admin.clinicId()))).andExpect(status().isForbidden());

        JsonNode updated = body(putJson(manager.token(), null, users + reception.userId() + "/clinic-roles",
                Map.of("roleId", role(admin, "veterinarian"), "clinicIds", List.of(admin.clinicId()))).andExpect(status().isOk()));
        assertThat(updated.get("clinicRoles").findValuesAsText("roleCode")).containsExactly("veterinarian");
        // Rol değişikliği hemen geçerlidir: yeni girişte yetkiler yeni role göre gelir.
        JsonNode relogged = relogin(reception).body();
        assertThat(relogged.get("roles").findValuesAsText("code")).containsExactly("veterinarian");
        assertThat(relogged.get("permissions").toString()).contains("clinical.write").doesNotContain("billing.manage");

        // Klinik yöneticisi yönetici rolü verebilir.
        putJson(admin.token(), null, users + reception.userId() + "/clinic-roles",
                Map.of("roleId", role(admin, "clinic_admin"), "clinicIds", List.of(admin.clinicId()))).andExpect(status().isOk());
        getJson(reception.token(), "/api/v1/users").andExpect(status().isForbidden());
    }

    @Test
    void branchManagerOnlyManagesStaffOfOwnBranches() throws Exception {
        Session admin = signup(OrgTestData.createPlan(superUserDataSource(), 3, 10));
        UUID second = createBranch(admin, "Beşiktaş Şubesi");
        Session manager = staff(admin, "branch_manager", admin.clinicId());
        Session ownVet = staff(admin, "veterinarian", admin.clinicId());
        Session otherVet = staff(admin, "veterinarian", second);

        putJson(manager.token(), null, "/api/v1/users/" + otherVet.userId() + "/clinic-roles",
                Map.of("roleId", role(admin, "receptionist"), "clinicIds", List.of(admin.clinicId()))).andExpect(status().isForbidden());
        postJson(manager.token(), "/api/v1/users/" + otherVet.userId() + "/deactivate", Map.of()).andExpect(status().isForbidden());
        putJson(manager.token(), null, "/api/v1/users/" + ownVet.userId() + "/clinic-roles",
                Map.of("roleId", role(admin, "veterinarian"), "clinicIds", List.of(admin.clinicId(), second))).andExpect(status().isForbidden());
        postJson(manager.token(), "/api/v1/users/" + ownVet.userId() + "/deactivate", Map.of()).andExpect(status().isOk());
    }

    @Test
    void lastActiveClinicAdminIsProtected() throws Exception {
        Session admin = signup(OrgTestData.createPlan(superUserDataSource(), 3, 10));
        createBranch(admin, "Üsküdar Şubesi");
        JsonNode me = body(getJson(admin.token(), "/api/v1/users/" + admin.userId()).andExpect(status().isOk()));
        List<String> assignments = me.get("clinicRoles").findValuesAsText("id");
        assertThat(assignments).hasSize(2);
        String users = "/api/v1/users/" + admin.userId();

        // İki şubedeki yönetici atamasından biri kaldırılabilir, sonuncusu kaldırılamaz.
        deleteJson(admin.token(), null, users + "/clinic-roles/" + assignments.get(0)).andExpect(status().isOk());
        deleteJson(admin.token(), null, users + "/clinic-roles/" + assignments.get(1))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LAST_CLINIC_ADMIN"));
        postJson(admin.token(), users + "/deactivate", Map.of()).andExpect(status().isUnprocessableEntity());
        putJson(admin.token(), null, users + "/clinic-roles",
                Map.of("roleId", role(admin, "veterinarian"), "clinicIds", List.of(admin.clinicId()))).andExpect(status().isForbidden());

        // İkinci yönetici varken ilk yönetici pasifleştirilebilir.
        Session second = staff(admin, "clinic_admin", admin.clinicId());
        postJson(second.token(), users + "/deactivate", Map.of()).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        assertThat(OrgTestData.count(superUserDataSource(), "SELECT count(*) FROM vet.app_users WHERE organization_id = ? AND is_active",
                admin.organizationId())).isEqualTo(1);
    }

    @Test
    void otherOrganizationStaffIs404_validationIs400() throws Exception {
        Session admin = signup(null);
        Session reception = staff(admin, "receptionist", admin.clinicId());
        Session other = signup(null);

        putJson(other.token(), null, "/api/v1/users/" + reception.userId() + "/clinic-roles",
                Map.of("roleId", role(other, "veterinarian"), "clinicIds", List.of(other.clinicId()))).andExpect(status().isNotFound());
        postJson(other.token(), "/api/v1/users/" + reception.userId() + "/deactivate", Map.of()).andExpect(status().isNotFound());
        putJson(admin.token(), null, "/api/v1/users/" + reception.userId() + "/clinic-roles",
                Map.of("roleId", role(admin, "veterinarian"), "clinicIds", List.of(other.clinicId()))).andExpect(status().isUnprocessableEntity());
        putJson(admin.token(), null, "/api/v1/users/" + reception.userId() + "/clinic-roles",
                Map.of("roleId", role(admin, "veterinarian"), "clinicIds", List.of())).andExpect(status().isBadRequest());
    }

    @Test
    void permissionMatrix_listsTurkishRoleNamesAndPermissionDescriptions_forAnyStaff() throws Exception {
        Session admin = signup(null);
        Session reception = staff(admin, "receptionist", admin.clinicId());

        JsonNode matrix = body(getJson(reception.token(), "/api/v1/permission-matrix").andExpect(status().isOk()));
        assertThat(matrix.get("roles").findValuesAsText("code"))
                .containsExactly("clinic_admin", "branch_manager", "veterinarian", "receptionist");
        assertThat(matrix.get("roles").findValuesAsText("name"))
                .containsExactly("Klinik yöneticisi", "Şube yöneticisi", "Veteriner hekim", "Resepsiyon");
        JsonNode receptionist = matrix.get("roles").get(3);
        assertThat(receptionist.get("permissions").toString()).contains("billing.manage").doesNotContain("clinical.write");
        assertThat(matrix.get("roles").get(1).get("permissions").toString()).doesNotContain("branches.manage");
        assertThat(matrix.get("permissions").toString()).contains("Klinik ve şube yönetimi", "Muayene ve tedavi kayıtlarını yönetme");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/permission-matrix"))
                .andExpect(status().isUnauthorized());
    }
}
