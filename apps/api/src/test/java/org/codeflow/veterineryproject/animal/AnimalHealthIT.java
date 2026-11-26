package org.codeflow.veterineryproject.animal;

import com.fasterxml.jackson.databind.JsonNode;
import org.codeflow.veterineryproject.common.test.ApiTestSupport;
import org.codeflow.veterineryproject.common.test.RegistrationTestData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.codeflow.veterineryproject.common.test.RegistrationTestData.*;
import static org.codeflow.veterineryproject.common.test.TenantTestData.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AnimalHealthIT extends ApiTestSupport {

    private static final String BASE = "/api/v1/animals/" + PATIENT_A1_S1;

    @BeforeAll
    static void seed() {
        RegistrationTestData.seed(superUserDataSource());
    }

    @Test
    void weightsAreRecordedAndLatestShownInSummary() throws Exception {
        String t = token(ORG_A_CODE, EMAIL_FRONTDESK_A1);
        postJson(t, BASE + "/weights", Map.of("weightKg", 4.2, "measuredAt", OffsetDateTime.now().minusDays(2).toString()))
                .andExpect(status().isCreated());
        postJson(t, BASE + "/weights", Map.of("weightKg", 4.6)).andExpect(status().isCreated());

        assertThat(body(getJson(t, BASE + "/weights").andExpect(status().isOk())).size()).isGreaterThanOrEqualTo(2);
        JsonNode summary = body(getJson(t, BASE + "/health-summary").andExpect(status().isOk()));
        assertThat(summary.path("latestWeight").path("weightKg").decimalValue().doubleValue()).isEqualTo(4.6);
    }

    @Test
    void futureOrInvalidWeightRejected() throws Exception {
        String t = token(ORG_A_CODE, EMAIL_FRONTDESK_A1);
        postJson(t, BASE + "/weights", Map.of("weightKg", 4, "measuredAt", OffsetDateTime.now().plusDays(3).toString()))
                .andExpect(status().isUnprocessableEntity());
        postJson(t, BASE + "/weights", Map.of("weightKg", 0)).andExpect(status().isBadRequest());
    }

    @Test
    void allergyCanBeAddedAndResolved() throws Exception {
        String t = token(ORG_A_CODE, EMAIL_FRONTDESK_A1);
        JsonNode a = body(postJson(t, BASE + "/allergies", Map.of("substance", "Penisilin", "severity", "SEVERE"))
                .andExpect(status().isCreated()));
        assertThat(a.path("active").asBoolean()).isTrue();

        postJson(t, BASE + "/allergies/" + a.path("id").asText() + "/resolve", Map.of()).andExpect(status().isOk());
        JsonNode active = body(getJson(t, BASE + "/allergies?activeOnly=true").andExpect(status().isOk()));
        for (JsonNode n : active) {
            assertThat(n.path("id").asText()).isNotEqualTo(a.path("id").asText());
        }
    }

    @Test
    void chipValidationAndDuplicate() throws Exception {
        String t = token(ORG_A_CODE, EMAIL_FRONTDESK_A1);
        postJson(t, BASE + "/identifiers", Map.of("identifierType", "MICROCHIP", "identifierValue", "ABC"))
                .andExpect(status().isUnprocessableEntity());
        Map<String, Object> chip = Map.of("identifierType", "MICROCHIP", "identifierValue", "900111222333444");
        postJson(t, BASE + "/identifiers", chip).andExpect(status().isCreated());
        postJson(t, BASE + "/identifiers", chip).andExpect(status().isConflict());
    }

    @Test
    void permissionsAndIsolation() throws Exception {
        postJson(token(ORG_A_CODE, EMAIL_RECEPTION_A1), BASE + "/weights", Map.of("weightKg", 4))
                .andExpect(status().isForbidden());
        getJson(token(ORG_A_CODE, EMAIL_FRONTDESK_A1), "/api/v1/animals/" + UUID.randomUUID() + "/health-summary")
                .andExpect(status().isNotFound());
        getJson(token(ORG_B_CODE, EMAIL_MANAGER_B1), BASE + "/health-summary").andExpect(status().isNotFound());
    }
}
