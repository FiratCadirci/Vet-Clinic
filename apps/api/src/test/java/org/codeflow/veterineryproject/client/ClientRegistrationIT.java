package org.codeflow.veterineryproject.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.codeflow.veterineryproject.common.test.ApiTestSupport;
import org.codeflow.veterineryproject.common.test.RegistrationTestData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.codeflow.veterineryproject.common.test.RegistrationTestData.*;
import static org.codeflow.veterineryproject.common.test.TenantTestData.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ClientRegistrationIT extends ApiTestSupport {

    private static final String CLINIC = BRANCH_A1_S1.toString();

    @BeforeAll
    static void seed() {
        RegistrationTestData.seed(superUserDataSource());
    }

    @Test
    void create_person_normalizesPhoneAndBuildsDisplayName() throws Exception {
        String token = token(ORG_A_CODE, EMAIL_MANAGER_A1);

        JsonNode client = body(postJson(token, CLINIC, "/api/v1/clients", person("Ayşe", "Yılmaz", "0 (555) 123-45-67", "Ayse@Example.com"))
                .andExpect(status().isCreated()));

        assertThat(client.get("displayName").asText()).isEqualTo("Ayşe Yılmaz");
        assertThat(client.get("clientType").asText()).isEqualTo("PERSON");
        assertThat(client.get("phone").asText()).isEqualTo("05551234567");
        assertThat(client.get("email").asText()).isEqualTo("ayse@example.com");
        assertThat(client.get("address").get("city").asText()).isEqualTo("İstanbul");
        assertThat(client.get("active").asBoolean()).isTrue();
        assertThat(client.get("marketingConsent").asBoolean()).isFalse();
        assertThat(client.get("possibleDuplicates").isEmpty()).isTrue();
        assertThat(client.has("identityNumberEnc")).isFalse();
    }

    @Test
    void create_business_requiresCompanyName_personRequiresFirstAndLastName() throws Exception {
        String token = token(ORG_A_CODE, EMAIL_MANAGER_A1);

        postJson(token, CLINIC, "/api/v1/clients", Map.of("clientType", "BUSINESS")).andExpect(status().isBadRequest());
        postJson(token, CLINIC, "/api/v1/clients", Map.of("clientType", "PERSON", "firstName", "Sadece")).andExpect(status().isBadRequest());
        postJson(token, CLINIC, "/api/v1/clients", Map.of("clientType", "COMPANY", "companyName", "X")).andExpect(status().isBadRequest());

        JsonNode business = body(postJson(token, CLINIC, "/api/v1/clients",
                Map.of("clientType", "BUSINESS", "companyName", "Patili Pet Ltd.", "taxNumber", "1234567890", "marketingConsent", true))
                .andExpect(status().isCreated()));
        assertThat(business.get("displayName").asText()).isEqualTo("Patili Pet Ltd.");
        assertThat(business.get("marketingConsent").asBoolean()).isTrue();
    }

    @Test
    void create_invalidPhoneOrChannel_returns400() throws Exception {
        String token = token(ORG_A_CODE, EMAIL_MANAGER_A1);

        postJson(token, CLINIC, "/api/v1/clients", person("A", "B", "abc", null)).andExpect(status().isBadRequest());
        Map<String, Object> bad = new HashMap<>(person("A", "B", "05550000000", null));
        bad.put("preferredChannel", "PIGEON");
        postJson(token, CLINIC, "/api/v1/clients", bad).andExpect(status().isBadRequest());
    }

    @Test
    void create_requiresClinicHeaderAndWritePermission() throws Exception {
        String token = token(ORG_A_CODE, EMAIL_MANAGER_A1);

        postJson(token, "/api/v1/clients", person("Başlık", "Yok", null, null)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_HEADER"));
        postJson(token, BRANCH_A2_S1.toString(), "/api/v1/clients", person("Yanlış", "Şube", null, null)).andExpect(status().isForbidden());
        postJson(token(ORG_A_CODE, EMAIL_READER_A1), BRANCH_A1_S2.toString(), "/api/v1/clients", person("Yetki", "Yok", null, null))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_samePhoneOrEmail_returns409UntilConfirmed() throws Exception {
        String token = token(ORG_A_CODE, EMAIL_MANAGER_A1);
        JsonNode first = body(postJson(token, CLINIC, "/api/v1/clients", person("Mehmet", "Kaya", "05320001122", "mehmet@dup.com")));

        Map<String, Object> dup = new java.util.HashMap<>(person("Mehmet", "Kaya", "0532 000 11 22", "MEHMET@dup.com"));
        JsonNode conflict = body(postJson(token, CLINIC, "/api/v1/clients", dup).andExpect(status().isConflict()));
        JsonNode list = conflict.at("/details/possibleDuplicates");
        assertThat(list).hasSize(1);
        assertThat(list.get(0).get("id").asText()).isEqualTo(first.get("id").asText());
        assertThat(list.get(0).get("matchedOn").asText()).isEqualTo("EMAIL,PHONE");

        dup.put("confirmDuplicate", true);
        JsonNode second = body(postJson(token, CLINIC, "/api/v1/clients", dup).andExpect(status().isCreated()));
        assertThat(second.get("id").asText()).isNotEqualTo(first.get("id").asText());
    }

    @Test
    void visibility_siblingBranchSees_otherGroupAndOrganizationGet404() throws Exception {
        String id = body(postJson(token(ORG_A_CODE, EMAIL_MANAGER_A1), CLINIC, "/api/v1/clients", person("Kardeş", "Şube", null, null)))
                .get("id").asText();

        getJson(token(ORG_A_CODE, EMAIL_READER_A1), "/api/v1/clients/" + id).andExpect(status().isOk());
        getJson(token(ORG_A_CODE, EMAIL_MANAGER_A2), "/api/v1/clients/" + id).andExpect(status().isNotFound());
        getJson(token(ORG_B_CODE, EMAIL_MANAGER_B1), "/api/v1/clients/" + id).andExpect(status().isNotFound());
        getJson(token(ORG_A_CODE, EMAIL_MANAGER_A1), "/api/v1/clients/" + UUID.randomUUID()).andExpect(status().isNotFound());
        patchJson(token(ORG_A_CODE, EMAIL_MANAGER_A2), "/api/v1/clients/" + id, Map.of("firstName", "Hack")).andExpect(status().isNotFound());
        postJson(token(ORG_B_CODE, EMAIL_MANAGER_B1), "/api/v1/clients/" + id + "/deactivate", Map.of()).andExpect(status().isNotFound());
        getJson(token(ORG_A_CODE, EMAIL_LIMITED_A1), "/api/v1/clients/" + id).andExpect(status().isForbidden());
    }

    @Test
    void list_isPagedSearchableByNameAndPhone_andScopedToClinicGroup() throws Exception {
        String token = token(ORG_A_CODE, EMAIL_MANAGER_A1);
        postJson(token, CLINIC, "/api/v1/clients", person("Zeynep", "Aranır", "05411112233", null)).andExpect(status().isCreated());
        postJson(token, CLINIC, "/api/v1/clients", person("Zeynel", "Bulunur", "05444445566", null)).andExpect(status().isCreated());

        getJson(token, "/api/v1/clients?q=zeyn&size=1").andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));
        getJson(token, "/api/v1/clients?q=0541 111 22 33").andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].displayName").value("Zeynep Aranır"));
        getJson(token, "/api/v1/clients?q=%25").andExpect(jsonPath("$.totalElements").value(0));

        String all = getJson(token, "/api/v1/clients?size=200").andReturn().getResponse().getContentAsString();
        assertThat(all).contains("Client A1S1", "Client A1S2");
        assertThat(all).doesNotContain("Client A2S1", "Client B1S1");
    }

    @Test
    void update_changesFieldsContactAndAddress_butNotType() throws Exception {
        String token = token(ORG_A_CODE, EMAIL_MANAGER_A1);
        String id = body(postJson(token, CLINIC, "/api/v1/clients", person("Eski", "Ad", "05001112233", "eski@x.com"))).get("id").asText();

        JsonNode updated = body(patchJson(token, "/api/v1/clients/" + id, Map.of(
                "firstName", "Yeni", "marketingConsent", true, "preferredChannel", "WHATSAPP",
                "contact", Map.of("phone", "05009998877", "email", ""),
                "address", Map.of("addressLine", "Yeni Mah. 5", "city", "Ankara")))
                .andExpect(status().isOk()));

        assertThat(updated.get("displayName").asText()).isEqualTo("Yeni Ad");
        assertThat(updated.get("clientType").asText()).isEqualTo("PERSON");
        assertThat(updated.get("phone").asText()).isEqualTo("05009998877");
        assertThat(updated.get("email").isNull()).isTrue();
        assertThat(updated.get("preferredChannel").asText()).isEqualTo("WHATSAPP");
        assertThat(updated.get("address").get("addressLine").asText()).isEqualTo("Yeni Mah. 5");
        assertThat(updated.get("address").get("countryCode").asText().trim()).isEqualTo("TR");
    }

    @Test
    void deactivateAndActivate_toggleFlag() throws Exception {
        String token = token(ORG_A_CODE, EMAIL_MANAGER_A1);
        String id = body(postJson(token, CLINIC, "/api/v1/clients", person("Pasif", "Olacak", null, null))).get("id").asText();

        postJson(token, "/api/v1/clients/" + id + "/deactivate", Map.of()).andExpect(jsonPath("$.active").value(false));
        getJson(token, "/api/v1/clients?active=false&q=Pasif").andExpect(jsonPath("$.totalElements").value(1));
        postJson(token, "/api/v1/clients/" + id + "/activate", Map.of()).andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void readerCanReadButNotWrite() throws Exception {
        String reader = token(ORG_A_CODE, EMAIL_READER_A1);
        String id = body(postJson(token(ORG_A_CODE, EMAIL_MANAGER_A1), CLINIC, "/api/v1/clients", person("Salt", "Okunur", null, null))).get("id").asText();

        getJson(reader, "/api/v1/clients").andExpect(status().isOk());
        patchJson(reader, "/api/v1/clients/" + id, Map.of("firstName", "X")).andExpect(status().isForbidden());
        postJson(reader, "/api/v1/clients/" + id + "/deactivate", Map.of()).andExpect(status().isForbidden());
    }

    static Map<String, Object> person(String first, String last, String phone, String email) {
        Map<String, Object> contact = new HashMap<>();
        if (phone != null) {
            contact.put("phone", phone);
        }
        if (email != null) {
            contact.put("email", email);
        }
        Map<String, Object> request = new HashMap<>();
        request.put("clientType", "PERSON");
        request.put("firstName", first);
        request.put("lastName", last);
        if (!contact.isEmpty()) {
            request.put("contact", contact);
            request.put("address", Map.of("addressLine", "Atatürk Cad. No:1", "city", "İstanbul", "district", "Kadıköy"));
        }
        return request;
    }
}
