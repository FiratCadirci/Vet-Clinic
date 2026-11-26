package org.codeflow.veterineryproject.patient;

import com.fasterxml.jackson.databind.JsonNode;
import org.codeflow.veterineryproject.common.test.ClinicalTestData;
import org.codeflow.veterineryproject.common.test.PatientsApiTestSupport;
import org.codeflow.veterineryproject.common.time.ApiDateTime;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.codeflow.veterineryproject.common.test.PatientsTestData.EMAIL_ALL_BRANCH_VET;
import static org.codeflow.veterineryproject.common.test.RegistrationTestData.*;
import static org.codeflow.veterineryproject.common.test.TenantTestData.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FG-C5 + FG-J1 + Senaryo 5: "Karamel" A1-Şube1'de (klinik grubu CA1) kayıtlı; A2-Şube1'de (ayrı klinik grubu CA2)
 * acil muayene olur. Görünürlük: aynı gruptaki şubeler veri paylaşır; başka grubun kaydı ve {@code clinical.read}
 * yetkisi olmayan rolün göremediği muayene/aşı kayıtları listede yer almaz, yalnızca sayısı bildirilir.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PatientHistoryIT extends PatientsApiTestSupport {

    private static final String S1 = BRANCH_A1_S1.toString();
    private static final String A2 = BRANCH_A2_S1.toString();
    private static final String S1_COMPLAINT = "Kadıköy kontrol şikâyeti";
    private static final String A2_COMPLAINT = "Beşiktaş acil şikâyeti";

    private String karamel;
    private String url;

    @BeforeAll
    void scenario() throws Exception {
        String frontdesk = token(ORG_A_CODE, EMAIL_FRONTDESK_A1);
        String vetS1 = token(ORG_A_CODE, EMAIL_VET_A1);
        String vetA2 = token(ORG_A_CODE, EMAIL_VET_A2);
        karamel = newAnimal(frontdesk, S1, newClient(frontdesk, S1, "Karamel", "Sahibi"), "Karamel");
        url = "/api/v1/patients/" + karamel + "/history";

        // A1-Şube1: muayene + aşı + randevu
        String encounter = body(postJson(vetS1, S1, "/api/v1/encounters",
                Map.of("animalId", karamel, "chiefComplaint", S1_COMPLAINT)).andExpect(status().isCreated())).get("id").asText();
        postJson(vetS1, S1, "/api/v1/vaccinations", Map.of("encounterId", encounter, "vaccineName", "Kuduz"))
                .andExpect(status().isCreated());
        OffsetDateTime start = OffsetDateTime.parse("2035-05-02T10:00:00+03:00");
        postJson(frontdesk, S1, "/api/v1/appointments", Map.of("animalId", karamel, "startsAt", start.toString(),
                "endsAt", start.plusMinutes(30).toString())).andExpect(status().isCreated());

        // Hasta CA2 grubundaki şubeye de açılır (acil başvuru) ve orada muayene olur.
        ClinicalTestData.update(superUserDataSource(),
                "INSERT INTO vet.animal_clinic_access (organization_id, animal_id, clinic_id) VALUES (?, ?::uuid, ?)",
                ORG_A, karamel, BRANCH_A2_S1);
        postJson(vetA2, A2, "/api/v1/encounters", Map.of("animalId", karamel, "chiefComplaint", A2_COMPLAINT))
                .andExpect(status().isCreated());
    }

    private JsonNode history(String token, String clinic) throws Exception {
        return body(getJson(token, clinic, url + "?size=50").andExpect(status().isOk()));
    }

    private static List<String> types(JsonNode history) {
        List<String> types = new ArrayList<>();
        history.get("content").forEach(i -> types.add(i.get("type").asText()));
        return types;
    }

    @Test
    void allBranchVeterinarianSeesBothBranches_nothingHidden() throws Exception {
        JsonNode h = history(token(ORG_A_CODE, EMAIL_ALL_BRANCH_VET), S1);
        assertThat(types(h)).containsExactlyInAnyOrder("ENCOUNTER", "ENCOUNTER", "VACCINATION", "APPOINTMENT");
        assertThat(h.get("hiddenCount").asLong()).isZero();
        assertThat(h.get("clinicalAccess").asBoolean()).isTrue();
        assertThat(h.toString()).contains(S1_COMPLAINT, A2_COMPLAINT);
    }

    @Test
    void siblingBranchVeterinarianSeesItsGroup_otherGroupIsHiddenAndCounted() throws Exception {
        JsonNode h = history(token(ORG_A_CODE, EMAIL_VET_A1_S2), BRANCH_A1_S2.toString());
        assertThat(types(h)).containsExactlyInAnyOrder("ENCOUNTER", "VACCINATION", "APPOINTMENT");
        assertThat(h.get("hiddenCount").asLong()).isEqualTo(1);
        assertThat(h.get("hiddenByType").get("encounters").asLong()).isEqualTo(1);
        assertThat(h.toString()).contains(S1_COMPLAINT).doesNotContain(A2_COMPLAINT);
    }

    @Test
    void otherGroupVeterinarianSeesOnlyOwnBranch_andLearnsHowManyAreHidden() throws Exception {
        JsonNode h = history(token(ORG_A_CODE, EMAIL_VET_A2), A2);
        assertThat(types(h)).containsExactly("ENCOUNTER");
        assertThat(h.get("content").get(0).get("clinicId").asText()).isEqualTo(A2);
        assertThat(h.get("hiddenCount").asLong()).isEqualTo(3);
        JsonNode hidden = h.get("hiddenByType");
        assertThat(hidden.get("encounters").asLong()).isEqualTo(1);
        assertThat(hidden.get("vaccinations").asLong()).isEqualTo(1);
        assertThat(hidden.get("appointments").asLong()).isEqualTo(1);
        // Gizli kaydın içeriği sızmaz.
        assertThat(h.toString()).doesNotContain(S1_COMPLAINT, "Kuduz");
    }

    @Test
    void employeeWithoutClinicalPermissionSeesAppointmentsOnly_clinicalRecordsCounted() throws Exception {
        JsonNode h = history(token(ORG_A_CODE, EMAIL_FRONTDESK_A1), S1);
        assertThat(h.get("clinicalAccess").asBoolean()).isFalse();
        assertThat(types(h)).containsExactly("APPOINTMENT");
        assertThat(h.get("hiddenByType").get("encounters").asLong()).isEqualTo(2);
        assertThat(h.get("hiddenByType").get("vaccinations").asLong()).isEqualTo(1);
        assertThat(h.get("hiddenByType").get("appointments").asLong()).isZero();
        assertThat(h.get("hiddenCount").asLong()).isEqualTo(3);
        assertThat(h.toString()).doesNotContain(S1_COMPLAINT, A2_COMPLAINT, "Kuduz");
    }

    @Test
    void historyIsChronologicalAndPaged() throws Exception {
        String token = token(ORG_A_CODE, EMAIL_ALL_BRANCH_VET);
        JsonNode all = history(token, S1);
        OffsetDateTime previous = null;
        for (JsonNode item : all.get("content")) {
            OffsetDateTime at = ApiDateTime.parseOffset(item.get("occurredAt").asText());
            if (previous != null) {
                assertThat(at).isBeforeOrEqualTo(previous);
            }
            previous = at;
        }
        // Gelecekteki randevu en üstte.
        assertThat(all.get("content").get(0).get("type").asText()).isEqualTo("APPOINTMENT");

        JsonNode page = body(getJson(token, S1, url + "?size=1&page=1").andExpect(status().isOk()));
        assertThat(page.get("content")).hasSize(1);
        assertThat(page.get("totalElements").asLong()).isEqualTo(4);
        assertThat(page.get("page").asInt()).isEqualTo(1);
        assertThat(body(getJson(token, S1, url + "?size=1000")).get("size").asInt()).isEqualTo(200);
    }

    @Test
    void otherOrganizationGets404_unauthorizedRoleGets403() throws Exception {
        getJson(token(ORG_B_CODE, EMAIL_MANAGER_B1), url).andExpect(status().isNotFound());
        getJson(token(ORG_A_CODE, EMAIL_MANAGER_A2), A2, "/api/v1/patients/" + PATIENT_A1_S1 + "/history")
                .andExpect(status().isNotFound());
        getJson(token(ORG_B_CODE, EMAIL_VET_B1), url).andExpect(status().isNotFound());
        getJson(token(ORG_A_CODE, EMAIL_LIMITED_A1), url).andExpect(status().isForbidden());
    }
}
