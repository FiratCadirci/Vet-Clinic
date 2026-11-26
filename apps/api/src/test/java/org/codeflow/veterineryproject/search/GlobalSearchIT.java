package org.codeflow.veterineryproject.search;

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
class GlobalSearchIT extends ApiTestSupport {

    @BeforeAll
    static void seed() {
        RegistrationTestData.seed(superUserDataSource());
    }

    @Test
    void findsAnimalAndOwnerByChipNumber() throws Exception {
        String t = token(ORG_A_CODE, EMAIL_FRONTDESK_A1);
        postJson(t, "/api/v1/animals/" + PATIENT_A1_S1 + "/identifiers",
                Map.of("identifierType", "MICROCHIP", "identifierValue", "900555666777888")).andExpect(status().isCreated());

        JsonNode res = body(getJson(t, "/api/v1/search?q=900555666777888").andExpect(status().isOk()));
        assertThat(res.path("animals")).hasSize(1);
        assertThat(res.path("animals").get(0).path("id").asText()).isEqualTo(PATIENT_A1_S1.toString());
        assertThat(res.path("animals").get(0).path("chipNo").asText()).isEqualTo("900555666777888");
    }

    @Test
    void findsClientByName() throws Exception {
        String t = token(ORG_A_CODE, EMAIL_FRONTDESK_A1);
        JsonNode res = body(getJson(t, "/api/v1/search?q=client").andExpect(status().isOk()));
        assertThat(res.path("clients").size()).isGreaterThan(0).isLessThanOrEqualTo(10);
    }

    @Test
    void tooShortQueryRejected() throws Exception {
        getJson(token(ORG_A_CODE, EMAIL_FRONTDESK_A1), "/api/v1/search?q=a").andExpect(status().isUnprocessableEntity());
    }

    @Test
    void otherOrganizationSeesNothingAndNoPermissionIsForbidden() throws Exception {
        JsonNode res = body(getJson(token(ORG_B_CODE, EMAIL_MANAGER_B1), "/api/v1/search?q=900555666777888")
                .andExpect(status().isOk()));
        assertThat(res.path("animals")).isEmpty();
        getJson(token(ORG_A_CODE, EMAIL_RECEPTION_A1), "/api/v1/search?q=client").andExpect(status().isForbidden());
    }
}
