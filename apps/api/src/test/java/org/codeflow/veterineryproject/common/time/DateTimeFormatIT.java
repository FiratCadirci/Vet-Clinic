package org.codeflow.veterineryproject.common.time;

import com.fasterxml.jackson.databind.JsonNode;
import org.codeflow.veterineryproject.common.test.ApiTestSupport;
import org.codeflow.veterineryproject.common.test.RegistrationTestData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.codeflow.veterineryproject.common.test.RegistrationTestData.*;
import static org.codeflow.veterineryproject.common.test.TenantTestData.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DateTimeFormatIT extends ApiTestSupport {

    private static final String PATTERN = "\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}";
    private static final String S1 = BRANCH_A1_S1.toString();

    @BeforeAll
    static void seed() {
        RegistrationTestData.seed(superUserDataSource());
    }

    @Test
    void responses_encounterAndHistoryUseDateHourMinuteOnly() throws Exception {
        String vet = token(ORG_A_CODE, EMAIL_VET_A1);
        JsonNode encounter = body(postJson(vet, S1, "/api/v1/encounters",
                Map.of("animalId", PATIENT_A1_S1.toString(), "chiefComplaint", "Format testi")).andExpect(status().isCreated()));

        assertAllDateTimesFormatted(encounter, 3);
        JsonNode history = body(getJson(vet, "/api/v1/animals/" + PATIENT_A1_S1 + "/history").andExpect(status().isOk()));
        assertAllDateTimesFormatted(history, 1);
        assertAllDateTimesFormatted(body(getJson(vet, "/api/v1/encounters/" + encounter.get("id").asText())), 3);
    }

    @Test
    void responses_userAndErrorTimestampsUseDateHourMinuteOnly() throws Exception {
        String manager = token(ORG_A_CODE, EMAIL_MANAGER_A1);

        assertAllDateTimesFormatted(body(getJson(manager, "/api/v1/users/" + MANAGER_A1).andExpect(status().isOk())), 1);
        JsonNode error = body(getJson(manager, "/api/v1/users/" + UUID.randomUUID()).andExpect(status().isNotFound()));
        assertThat(error.get("timestamp").asText()).matches(PATTERN);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/users"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void requests_acceptApiFormatAndIsoAndRejectGarbage() throws Exception {
        String vet = token(ORG_A_CODE, EMAIL_VET_A1);
        String encounter = body(postJson(vet, S1, "/api/v1/encounters",
                Map.of("animalId", PATIENT_A1_S1.toString(), "chiefComplaint", "İstek formatı")).andExpect(status().isCreated())).get("id").asText();
        String apiFormat = java.time.LocalDateTime.now(ApiDateTime.ZONE).minusHours(1).format(ApiDateTime.FORMAT);

        JsonNode withApiFormat = body(postJson(vet, S1, "/api/v1/vaccinations",
                Map.of("encounterId", encounter, "vaccineName", "A", "administeredAt", apiFormat)).andExpect(status().isCreated()));
        assertThat(withApiFormat.get("administeredAt").asText()).isEqualTo(apiFormat);

        postJson(vet, S1, "/api/v1/vaccinations", Map.of("encounterId", encounter, "vaccineName", "B",
                "administeredAt", java.time.OffsetDateTime.now().minusHours(2).toString())).andExpect(status().isCreated());
        postJson(vet, S1, "/api/v1/vaccinations", Map.of("encounterId", encounter, "vaccineName", "C",
                "administeredAt", "yarın sabah")).andExpect(status().isBadRequest());
    }

    /** "...At" ile biten tüm alanlar yyyy-MM-dd HH:mm olmalı; en az minimum kadar alan bulunmalı. */
    private void assertAllDateTimesFormatted(JsonNode root, int minimum) {
        List<String> values = new ArrayList<>();
        collect(root, values);
        assertThat(values).hasSizeGreaterThanOrEqualTo(minimum).allMatch(v -> v.matches(PATTERN));
    }

    private void collect(JsonNode node, List<String> out) {
        if (node.isObject()) {
            node.fields().forEachRemaining(e -> {
                if (e.getKey().endsWith("At") && e.getValue().isTextual()) {
                    out.add(e.getValue().asText());
                } else {
                    collect(e.getValue(), out);
                }
            });
        } else if (node.isArray()) {
            node.forEach(child -> collect(child, out));
        }
    }
}
