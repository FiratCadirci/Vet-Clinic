package org.codeflow.veterineryproject.user;

import com.fasterxml.jackson.databind.JsonNode;
import org.codeflow.veterineryproject.common.test.ApiTestSupport;
import org.codeflow.veterineryproject.common.test.RegistrationTestData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.codeflow.veterineryproject.common.test.RegistrationTestData.*;
import static org.codeflow.veterineryproject.common.test.TenantTestData.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class VeterinarianProfileIT extends ApiTestSupport {

    @BeforeAll
    static void seed() {
        RegistrationTestData.seed(superUserDataSource());
    }

    @Test
    void manager_createsProfileForUserWithoutOne() throws Exception {
        String manager = token(ORG_A_CODE, EMAIL_MANAGER_A1);

        JsonNode created = body(postJson(manager, "/api/v1/users/" + VET_NOPROFILE_A1 + "/veterinarian",
                Map.of("licenseNo", "VET-NEW-1", "specialty", "Cerrahi")).andExpect(status().isCreated()));

        assertThat(created.get("userId").asText()).isEqualTo(VET_NOPROFILE_A1.toString());
        assertThat(created.get("licenseNo").asText()).isEqualTo("VET-NEW-1");
        assertThat(created.get("active").asBoolean()).isTrue();
        getJson(manager, "/api/v1/users/" + VET_NOPROFILE_A1 + "/veterinarian").andExpect(status().isOk());
    }

    @Test
    void duplicateProfileOrLicenseReturns409() throws Exception {
        String manager = token(ORG_A_CODE, EMAIL_MANAGER_A1);

        postJson(manager, "/api/v1/users/" + VET_A1 + "/veterinarian", Map.of("licenseNo", "BASKA-LISANS")).andExpect(status().isConflict());
        postJson(manager, "/api/v1/users/" + VET_NOPROFILE_A1 + "/veterinarian", Map.of("licenseNo", "LIC-A1")).andExpect(status().isConflict());
    }

    @Test
    void userOfAnotherGroupOrOrganizationIsNotFoundAndNonManagerIsForbidden() throws Exception {
        String manager = token(ORG_A_CODE, EMAIL_MANAGER_A1);
        String vet = token(ORG_A_CODE, EMAIL_VET_A1);

        postJson(manager, "/api/v1/users/" + VET_B1 + "/veterinarian", Map.of("licenseNo", "X-1")).andExpect(status().isNotFound());
        getJson(manager, "/api/v1/users/" + VET_B1 + "/veterinarian").andExpect(status().isNotFound());
        postJson(vet, "/api/v1/users/" + VET_NOPROFILE_A1 + "/veterinarian", Map.of("licenseNo", "X-2")).andExpect(status().isForbidden());
        postJson(manager, "/api/v1/users/" + VET_NOPROFILE_A1 + "/veterinarian", Map.of("licenseNo", " ")).andExpect(status().isBadRequest());
    }
}
