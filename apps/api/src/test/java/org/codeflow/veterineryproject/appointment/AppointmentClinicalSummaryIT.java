package org.codeflow.veterineryproject.appointment;

import com.fasterxml.jackson.databind.JsonNode;
import org.codeflow.veterineryproject.common.test.ApiTestSupport;
import org.codeflow.veterineryproject.common.test.RegistrationTestData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.codeflow.veterineryproject.common.test.ClinicalTestData.appointment;
import static org.codeflow.veterineryproject.common.test.RegistrationTestData.*;
import static org.codeflow.veterineryproject.common.test.TenantTestData.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AppointmentClinicalSummaryIT extends ApiTestSupport {

    private static final String S1 = BRANCH_A1_S1.toString();

    @BeforeAll
    static void seed() {
        RegistrationTestData.seed(superUserDataSource());
    }

    private String url(UUID appointment) {
        return "/api/v1/appointments/" + appointment + "/clinical-summary";
    }

    @Test
    void summary_withoutEncounterIsEmpty() throws Exception {
        String vet = token(ORG_A_CODE, EMAIL_VET_A1);
        UUID appointment = appointment(superUserDataSource(), ORG_A, BRANCH_A1_S1, PATIENT_A1_S1, "CONFIRMED");

        JsonNode summary = body(getJson(vet, S1, url(appointment)).andExpect(status().isOk()));

        assertThat(summary.get("appointmentId").asText()).isEqualTo(appointment.toString());
        assertThat(summary.get("encounters")).isEmpty();
    }

    @Test
    void summary_includesEncounterTreatmentsAndVaccinations() throws Exception {
        String vet = token(ORG_A_CODE, EMAIL_VET_A1);
        UUID appointment = appointment(superUserDataSource(), ORG_A, BRANCH_A1_S1, PATIENT_A1_S1, "CONFIRMED");
        String encounter = body(postJson(vet, S1, "/api/v1/appointments/" + appointment + "/encounter", Map.of("chiefComplaint", "Kontrol"))
                .andExpect(status().isCreated())).get("id").asText();
        postJson(vet, S1, "/api/v1/encounters/" + encounter + "/treatments", Map.of("itemType", "PROCEDURE", "name", "Pansuman"))
                .andExpect(status().isCreated());
        postJson(vet, S1, "/api/v1/vaccinations", Map.of("encounterId", encounter, "vaccineName", "Kuduz"))
                .andExpect(status().isCreated());

        JsonNode summary = body(getJson(vet, S1, url(appointment)).andExpect(status().isOk()));

        assertThat(summary.get("encounters")).hasSize(1);
        JsonNode item = summary.get("encounters").get(0);
        assertThat(item.get("encounter").get("id").asText()).isEqualTo(encounter);
        assertThat(item.get("encounter").get("appointmentId").asText()).isEqualTo(appointment.toString());
        assertThat(item.get("encounter").get("treatments")).hasSize(1);
        assertThat(item.get("encounter").get("treatments").get(0).get("name").asText()).isEqualTo("Pansuman");
        assertThat(item.get("vaccinations")).hasSize(1);
        assertThat(item.get("vaccinations").get(0).get("vaccineName").asText()).isEqualTo("Kuduz");
    }

    @Test
    void summary_excludesWalkInEncountersOfSameAnimal() throws Exception {
        String vet = token(ORG_A_CODE, EMAIL_VET_A1);
        UUID appointment = appointment(superUserDataSource(), ORG_A, BRANCH_A1_S1, PATIENT_A1_S1, "CONFIRMED");
        postJson(vet, S1, "/api/v1/encounters", Map.of("animalId", PATIENT_A1_S1.toString(), "chiefComplaint", "Randevusuz"))
                .andExpect(status().isCreated());

        JsonNode summary = body(getJson(vet, S1, url(appointment)).andExpect(status().isOk()));

        assertThat(summary.get("encounters")).isEmpty();
    }

    @Test
    void summary_unknownAndForeignAppointmentsAreNotFound() throws Exception {
        String vet = token(ORG_A_CODE, EMAIL_VET_A1);
        UUID otherGroup = appointment(superUserDataSource(), ORG_A, BRANCH_A2_S1, PATIENT_A2_S1, "CONFIRMED");
        UUID otherOrg = appointment(superUserDataSource(), ORG_B, BRANCH_B1_S1, PATIENT_B1_S1, "CONFIRMED");

        getJson(vet, S1, url(UUID.randomUUID())).andExpect(status().isNotFound());
        getJson(vet, S1, url(otherGroup)).andExpect(status().isNotFound());
        getJson(vet, S1, url(otherOrg)).andExpect(status().isNotFound());
    }

    @Test
    void summary_requiresAuthenticationAndClinicalPermission() throws Exception {
        UUID appointment = appointment(superUserDataSource(), ORG_A, BRANCH_A1_S1, PATIENT_A1_S1, "CONFIRMED");

        getJson("invalid", S1, url(appointment)).andExpect(status().isUnauthorized());
        getJson(token(ORG_A_CODE, EMAIL_RECEPTION_A1), S1, url(appointment)).andExpect(status().isOk());
        getJson(token(ORG_A_CODE, EMAIL_LIMITED_A1), S1, url(appointment)).andExpect(status().isForbidden());
    }
}
