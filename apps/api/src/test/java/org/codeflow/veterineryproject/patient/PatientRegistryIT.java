package org.codeflow.veterineryproject.patient;

import com.fasterxml.jackson.databind.JsonNode;
import org.codeflow.veterineryproject.common.test.PatientsApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.codeflow.veterineryproject.common.test.RegistrationTestData.*;
import static org.codeflow.veterineryproject.common.test.TenantTestData.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** FG-B1 / FG-B2 (sahip–hasta kaydı, mikroçip organizasyon içinde tekil), FOG-8 (listeler sayfalı, en fazla 200). */
@SpringBootTest
@AutoConfigureMockMvc
class PatientRegistryIT extends PatientsApiTestSupport {

    private static final String S1 = BRANCH_A1_S1.toString();

    private static Map<String, Object> chip(String value) {
        return Map.of("identifierType", "MICROCHIP", "identifierValue", value);
    }

    @Test
    void microchipIsUniqueWithinTheOrganization_butNotAcrossOrganizations() throws Exception {
        String a1 = token(ORG_A_CODE, EMAIL_FRONTDESK_A1);
        String a2 = token(ORG_A_CODE, EMAIL_MANAGER_A2);
        String b1 = token(ORG_B_CODE, EMAIL_MANAGER_B1);
        String chipNo = "9002600" + String.format("%08d", System.nanoTime() % 100_000_000L);

        String first = newAnimal(a1, S1, newClient(a1, S1, "Cip", "Bir"), "Cip1");
        String second = newAnimal(a1, S1, newClient(a1, S1, "Cip", "Iki"), "Cip2");
        postJson(a1, S1, "/api/v1/animals/" + first + "/identifiers", chip(chipNo)).andExpect(status().isCreated());
        postJson(a1, S1, "/api/v1/animals/" + second + "/identifiers", chip(chipNo)).andExpect(status().isConflict());

        // Aynı organizasyonun başka klinik grubu da aynı numarayı kullanamaz (kayıt ona görünmese bile).
        String a2Branch = BRANCH_A2_S1.toString();
        String otherGroup = newAnimal(a2, a2Branch, newClient(a2, a2Branch, "Cip", "Uc"), "Cip3");
        postJson(a2, a2Branch, "/api/v1/animals/" + otherGroup + "/identifiers", chip(chipNo)).andExpect(status().isConflict());

        // Başka organizasyon aynı numarayı kaydedebilir.
        String b1Branch = BRANCH_B1_S1.toString();
        String otherOrg = newAnimal(b1, b1Branch, newClient(b1, b1Branch, "Cip", "Dort"), "Cip4");
        postJson(b1, b1Branch, "/api/v1/animals/" + otherOrg + "/identifiers", chip(chipNo)).andExpect(status().isCreated());

        // Hasta listesi mikroçiple bulur ve satırda gösterir.
        JsonNode found = body(getJson(a1, S1, "/api/v1/patients?q=" + chipNo).andExpect(status().isOk()));
        assertThat(found.get("totalElements").asLong()).isEqualTo(1);
        assertThat(found.get("content").get(0).get("microchipNo").asText()).isEqualTo(chipNo);
    }

    @Test
    void patientRowsCarryOwnerAndBranch_andSearchByOwnerPhone() throws Exception {
        String t = token(ORG_A_CODE, EMAIL_FRONTDESK_A1);
        String phone = uniquePhone();
        String client = body(postJson(t, S1, "/api/v1/clients", Map.of("clientType", "PERSON", "firstName", "Telefon",
                "lastName", "Arama", "contact", Map.of("phone", phone))).andExpect(status().isCreated())).get("id").asText();
        String first = newAnimal(t, S1, client, "Pamuk");
        newAnimal(t, S1, client, "Boncuk");

        JsonNode byPhone = body(getJson(t, S1, "/api/v1/patients?q=0" + phone).andExpect(status().isOk()));
        assertThat(byPhone.get("totalElements").asLong()).isEqualTo(2);

        JsonNode row = body(getJson(t, S1, "/api/v1/patients/" + first).andExpect(status().isOk()));
        assertThat(row.get("ownerId").asText()).isEqualTo(client);
        assertThat(row.get("ownerName").asText()).isEqualTo("Telefon Arama");
        assertThat(row.get("ownerPhone").asText()).contains(phone.substring(2));
        assertThat(row.get("registeredClinicId").asText()).isEqualTo(S1);
        assertThat(row.get("speciesCode").asText()).isEqualTo("DOG");

        // Sahip birden fazla hayvana sahip olabilir (FG-B1); sahip kartı hastaları ownerId filtresiyle listelenir.
        JsonNode animals = body(getJson(t, S1, "/api/v1/clients/" + client + "/animals").andExpect(status().isOk()));
        assertThat(animals.get("totalElements").asLong()).isEqualTo(2);
        JsonNode owned = body(getJson(t, S1, "/api/v1/patients?ownerId=" + client).andExpect(status().isOk()));
        assertThat(owned.get("totalElements").asLong()).isEqualTo(2);
        assertThat(body(getJson(t, S1, "/api/v1/patients?ownerId=" + client + "&q=Bon")).get("totalElements").asLong())
                .isEqualTo(1);
    }

    @Test
    void ownerAndPatientListsArePagedWithAtMost200Rows() throws Exception {
        String t = token(ORG_A_CODE, EMAIL_FRONTDESK_A1);
        for (String path : List.of("/api/v1/patients", "/api/v1/clients", "/api/v1/animals")) {
            JsonNode page = body(getJson(t, S1, path + "?size=1000").andExpect(status().isOk()));
            assertThat(page.get("size").asInt()).as(path).isEqualTo(200);
            assertThat(page.get("content").size()).as(path).isLessThanOrEqualTo(200);
        }
        newAnimal(t, S1, newClient(t, S1, "Sayfa", "Bir"), "Sayfa1");
        newAnimal(t, S1, newClient(t, S1, "Sayfa", "Iki"), "Sayfa2");
        JsonNode first = body(getJson(t, S1, "/api/v1/patients?q=Sayfa&size=1&page=0").andExpect(status().isOk()));
        JsonNode second = body(getJson(t, S1, "/api/v1/patients?q=Sayfa&size=1&page=1").andExpect(status().isOk()));
        assertThat(first.get("totalElements").asLong()).isEqualTo(2);
        assertThat(first.get("totalPages").asInt()).isEqualTo(2);
        List<String> names = new ArrayList<>();
        names.add(first.get("content").get(0).get("name").asText());
        names.add(second.get("content").get(0).get("name").asText());
        assertThat(names).containsExactly("Sayfa1", "Sayfa2");
    }

    @Test
    void otherOrganizationOrGroupGets404_unauthorizedGets403() throws Exception {
        getJson(token(ORG_B_CODE, EMAIL_MANAGER_B1), "/api/v1/patients/" + PATIENT_A1_S1).andExpect(status().isNotFound());
        getJson(token(ORG_A_CODE, EMAIL_MANAGER_A2), BRANCH_A2_S1.toString(), "/api/v1/patients/" + PATIENT_A1_S1)
                .andExpect(status().isNotFound());
        JsonNode otherOrgList = body(getJson(token(ORG_B_CODE, EMAIL_MANAGER_B1), "/api/v1/patients?q=A1S1")
                .andExpect(status().isOk()));
        assertThat(otherOrgList.get("totalElements").asLong()).isZero();
        getJson(token(ORG_A_CODE, EMAIL_LIMITED_A1), "/api/v1/patients").andExpect(status().isForbidden());
        getJson(token(ORG_A_CODE, EMAIL_VET_A1), "/api/v1/patients").andExpect(status().isForbidden());
    }
}
