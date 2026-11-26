package org.codeflow.veterineryproject.animal;

import com.fasterxml.jackson.databind.JsonNode;
import org.codeflow.veterineryproject.common.test.ApiTestSupport;
import org.codeflow.veterineryproject.common.test.RegistrationTestData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.codeflow.veterineryproject.common.test.RegistrationTestData.*;
import static org.codeflow.veterineryproject.common.test.TenantTestData.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AnimalRegistrationIT extends ApiTestSupport {

    private static final String CLINIC = BRANCH_A1_S1.toString();
    private static UUID dogId;
    private static UUID catId;
    private static UUID labradorId;
    private static UUID persianId;

    @BeforeAll
    static void seed() throws Exception {
        RegistrationTestData.seed(superUserDataSource());
        try (Connection c = superUserDataSource().getConnection()) {
            dogId = species(c, "DOG");
            catId = species(c, "CAT");
            labradorId = breed(c, dogId, "Labrador-IT");
            persianId = breed(c, catId, "Persian-IT");
        }
    }

    @Test
    void create_generatesPatientNoAndReturnsOwners() throws Exception {
        String token = token(ORG_A_CODE, EMAIL_MANAGER_A1);
        String client = newClient(token, "Sahip", "Bir");

        JsonNode animal = body(postJson(token, CLINIC, "/api/v1/animals", animal("Pamuk", dogId, labradorId, owners(client, true)))
                .andExpect(status().isCreated()));

        assertThat(animal.get("patientNo").asText()).matches("P-\\d{6}");
        assertThat(animal.get("speciesName").asText()).isNotBlank();
        assertThat(animal.get("breedName").asText()).isEqualTo("Labrador-IT");
        assertThat(animal.get("sex").asText()).isEqualTo("UNKNOWN");
        assertThat(animal.get("owners")).hasSize(1);
        assertThat(animal.get("owners").get(0).get("primaryContact").asBoolean()).isTrue();
        assertThat(animal.get("owners").get(0).get("clientName").asText()).isEqualTo("Sahip Bir");
    }

    @Test
    void create_patientNumbersAreUniqueUnderConcurrency() throws Exception {
        String token = token(ORG_A_CODE, EMAIL_MANAGER_A1);
        String client = newClient(token, "Eşzamanlı", "Sahip");
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<String>> jobs = new ArrayList<>();
            for (int i = 0; i < 16; i++) {
                int n = i;
                jobs.add(() -> body(postJson(token, CLINIC, "/api/v1/animals", animal("Paralel" + n, dogId, null, owners(client, true)))
                        .andExpect(status().isCreated())).get("patientNo").asText());
            }
            Set<String> numbers = new HashSet<>();
            for (Future<String> f : pool.invokeAll(jobs)) {
                numbers.add(f.get());
            }
            assertThat(numbers).hasSize(16);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void create_ownerRules() throws Exception {
        String token = token(ORG_A_CODE, EMAIL_MANAGER_A1);
        String c1 = newClient(token, "Sahip", "İki");
        String c2 = newClient(token, "Sahip", "Üç");

        postJson(token, CLINIC, "/api/v1/animals", animal("A", dogId, null, List.of())).andExpect(status().isBadRequest());
        postJson(token, CLINIC, "/api/v1/animals", animal("A", dogId, null, owners(c1, false))).andExpect(status().isBadRequest());
        postJson(token, CLINIC, "/api/v1/animals", animal("A", dogId, null,
                List.of(owner(c1, true), owner(c2, true)))).andExpect(status().isBadRequest());
        postJson(token, CLINIC, "/api/v1/animals", animal("A", dogId, null,
                List.of(owner(c1, true), owner(c1, false)))).andExpect(status().isBadRequest());
        postJson(token, CLINIC, "/api/v1/animals", animal("A", dogId, null, owners(UUID.randomUUID().toString(), true)))
                .andExpect(status().isUnprocessableEntity());

        postJson(token, CLINIC, "/api/v1/clients/" + c2 + "/deactivate", Map.of()).andExpect(status().isOk());
        postJson(token, CLINIC, "/api/v1/animals", animal("A", dogId, null, owners(c2, true))).andExpect(status().isUnprocessableEntity());

        postJson(token, CLINIC, "/api/v1/animals", animal("Çift", dogId, null, List.of(owner(c1, true)))).andExpect(status().isCreated());
    }

    @Test
    void create_validation() throws Exception {
        String token = token(ORG_A_CODE, EMAIL_MANAGER_A1);
        String client = newClient(token, "Doğrula", "Sahip");

        postJson(token, "/api/v1/animals", animal("A", dogId, null, owners(client, true)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MISSING_HEADER"));
        postJson(token, CLINIC, "/api/v1/animals", animal("A", dogId, persianId, owners(client, true))).andExpect(status().isUnprocessableEntity());
        postJson(token, CLINIC, "/api/v1/animals", animal("A", UUID.randomUUID(), null, owners(client, true))).andExpect(status().isUnprocessableEntity());

        Map<String, Object> blank = animal(" ", dogId, null, owners(client, true));
        postJson(token, CLINIC, "/api/v1/animals", blank).andExpect(status().isBadRequest());
        Map<String, Object> future = animal("A", dogId, null, owners(client, true));
        future.put("birthDate", LocalDate.now().plusDays(1).toString());
        postJson(token, CLINIC, "/api/v1/animals", future).andExpect(status().isBadRequest());
        Map<String, Object> badSex = animal("A", dogId, null, owners(client, true));
        badSex.put("sex", "ROBOT");
        postJson(token, CLINIC, "/api/v1/animals", badSex).andExpect(status().isBadRequest());
    }

    @Test
    void permissions_readerReadsButCannotWrite_limitedCannotRead() throws Exception {
        String manager = token(ORG_A_CODE, EMAIL_MANAGER_A1);
        String client = newClient(manager, "Yetki", "Sahip");
        String id = body(postJson(manager, CLINIC, "/api/v1/animals", animal("Yetki", dogId, null, owners(client, true)))).get("id").asText();

        String reader = token(ORG_A_CODE, EMAIL_READER_A1);
        getJson(reader, "/api/v1/animals/" + id).andExpect(status().isOk());
        getJson(reader, "/api/v1/animals").andExpect(status().isOk());
        postJson(reader, BRANCH_A1_S2.toString(), "/api/v1/animals", animal("X", dogId, null, owners(client, true))).andExpect(status().isForbidden());
        patchJson(reader, "/api/v1/animals/" + id, Map.of("name", "X")).andExpect(status().isForbidden());
        postJson(reader, "/api/v1/animals/" + id + "/deceased", Map.of("deceasedAt", LocalDate.now().toString())).andExpect(status().isForbidden());

        String limited = token(ORG_A_CODE, EMAIL_LIMITED_A1);
        getJson(limited, "/api/v1/animals/" + id).andExpect(status().isForbidden());
        getJson(limited, "/api/v1/species").andExpect(status().isForbidden());
    }

    @Test
    void visibility_siblingSees_otherGroupAndOrganizationGet404() throws Exception {
        String manager = token(ORG_A_CODE, EMAIL_MANAGER_A1);
        String client = newClient(manager, "Görünür", "Sahip");
        String id = body(postJson(manager, CLINIC, "/api/v1/animals", animal("Görünür", dogId, null, owners(client, true)))).get("id").asText();

        getJson(token(ORG_A_CODE, EMAIL_READER_A1), "/api/v1/animals/" + id).andExpect(status().isOk());
        getJson(token(ORG_A_CODE, EMAIL_MANAGER_A2), "/api/v1/animals/" + id).andExpect(status().isNotFound());
        getJson(token(ORG_B_CODE, EMAIL_MANAGER_B1), "/api/v1/animals/" + id).andExpect(status().isNotFound());
        getJson(manager, "/api/v1/animals/" + UUID.randomUUID()).andExpect(status().isNotFound());
        patchJson(token(ORG_B_CODE, EMAIL_MANAGER_B1), "/api/v1/animals/" + id, Map.of("name", "Hack")).andExpect(status().isNotFound());
        getJson(token(ORG_A_CODE, EMAIL_MANAGER_A2), "/api/v1/clients/" + client + "/animals").andExpect(status().isNotFound());

        // Başka organizasyonun müşterisi hastaya bağlanamaz
        String clientB = body(postJson(token(ORG_B_CODE, EMAIL_MANAGER_B1), BRANCH_B1_S1.toString(), "/api/v1/clients",
                Map.of("clientType", "BUSINESS", "companyName", "B Şirketi"))).get("id").asText();
        postJson(manager, CLINIC, "/api/v1/animals", animal("Z", dogId, null, owners(clientB, true))).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void list_searchByNamePatientNoAndOwner_andClientAnimals() throws Exception {
        String token = token(ORG_A_CODE, EMAIL_MANAGER_A1);
        String client = newClient(token, "Aranacak", "Kişi");
        JsonNode a = body(postJson(token, CLINIC, "/api/v1/animals", animal("Fındık-Ara", dogId, null, owners(client, true))));
        postJson(token, CLINIC, "/api/v1/animals", animal("Fındık-Ara2", catId, persianId, owners(client, true))).andExpect(status().isCreated());

        getJson(token, "/api/v1/animals?q=fındık-ara&size=1").andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content.length()").value(1));
        getJson(token, "/api/v1/animals?q=" + a.get("patientNo").asText()).andExpect(jsonPath("$.totalElements").value(1));
        getJson(token, "/api/v1/animals?q=aranacak").andExpect(jsonPath("$.totalElements").value(2));
        getJson(token, "/api/v1/animals?q=fındık-ara&speciesId=" + catId).andExpect(jsonPath("$.totalElements").value(1));
        getJson(token, "/api/v1/animals?q=%25").andExpect(jsonPath("$.totalElements").value(0));
        getJson(token, "/api/v1/clients/" + client + "/animals").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        getJson(token, "/api/v1/clients/" + UUID.randomUUID() + "/animals").andExpect(status().isNotFound());
    }

    @Test
    void species_andBreeds() throws Exception {
        String token = token(ORG_A_CODE, EMAIL_MANAGER_A1);

        String species = getJson(token, "/api/v1/species").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(species).contains(dogId.toString(), catId.toString());
        String breeds = getJson(token, "/api/v1/species/" + dogId + "/breeds").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(breeds).contains("Labrador-IT").doesNotContain("Persian-IT");
        getJson(token, "/api/v1/species/" + UUID.randomUUID() + "/breeds").andExpect(status().isNotFound());
    }

    @Test
    void update_changesFieldsButNotSpecies_andValidatesBreed() throws Exception {
        String token = token(ORG_A_CODE, EMAIL_MANAGER_A1);
        String client = newClient(token, "Güncel", "Sahip");
        String id = body(postJson(token, CLINIC, "/api/v1/animals", animal("Eski", dogId, null, owners(client, true)))).get("id").asText();

        JsonNode updated = body(patchJson(token, "/api/v1/animals/" + id, Map.of(
                "name", "Yeni", "breedId", labradorId, "sex", "FEMALE", "color", "Sarı",
                "sterilized", true, "sterilizationDate", LocalDate.now().minusDays(3).toString()))
                .andExpect(status().isOk()));

        assertThat(updated.get("name").asText()).isEqualTo("Yeni");
        assertThat(updated.get("speciesId").asText()).isEqualTo(dogId.toString());
        assertThat(updated.get("breedName").asText()).isEqualTo("Labrador-IT");
        assertThat(updated.get("sterilized").asBoolean()).isTrue();
        patchJson(token, "/api/v1/animals/" + id, Map.of("breedId", persianId)).andExpect(status().isUnprocessableEntity());
        patchJson(token, "/api/v1/animals/" + id, Map.of("name", " ")).andExpect(status().isBadRequest());
    }

    @Test
    void owners_addPrimaryTransferAndEndRules() throws Exception {
        String token = token(ORG_A_CODE, EMAIL_MANAGER_A1);
        String c1 = newClient(token, "Birinci", "Sahip");
        String c2 = newClient(token, "İkinci", "Sahip");
        JsonNode animal = body(postJson(token, CLINIC, "/api/v1/animals", animal("Ortak", dogId, null, owners(c1, true))));
        String id = animal.get("id").asText();
        String ownerOne = animal.get("owners").get(0).get("id").asText();

        postJson(token, "/api/v1/animals/" + id + "/owners", owner(c1, false)).andExpect(status().isConflict());
        postJson(token, "/api/v1/animals/" + id + "/owners", owner(UUID.randomUUID().toString(), false)).andExpect(status().isUnprocessableEntity());

        JsonNode withSecond = body(postJson(token, "/api/v1/animals/" + id + "/owners", owner(c2, true)).andExpect(status().isCreated()));
        assertThat(withSecond.get("owners")).hasSize(2);
        assertThat(primaryCount(withSecond)).isEqualTo(1);
        String ownerTwo = ownerId(withSecond, c2);

        postJson(token, "/api/v1/animals/" + id + "/owners/" + ownerTwo + "/end", Map.of()).andExpect(status().isUnprocessableEntity());

        JsonNode afterEnd = body(postJson(token, "/api/v1/animals/" + id + "/owners/" + ownerOne + "/end", Map.of()).andExpect(status().isOk()));
        assertThat(afterEnd.get("owners").get(0).get("validTo").isNull()).isFalse();
        postJson(token, "/api/v1/animals/" + id + "/owners/" + ownerOne + "/end", Map.of()).andExpect(status().isNotFound());
        postJson(token, "/api/v1/animals/" + id + "/owners/" + ownerTwo + "/end", Map.of()).andExpect(status().isUnprocessableEntity());

        postJson(token, "/api/v1/animals/" + id + "/owners", owner(c1, false)).andExpect(status().isCreated());
        postJson(token, "/api/v1/animals/" + id + "/owners/" + ownerId(body(getJson(token, "/api/v1/animals/" + id)), c1) + "/primary", Map.of())
                .andExpect(status().isOk());
    }

    @Test
    void deceased_rules() throws Exception {
        String token = token(ORG_A_CODE, EMAIL_MANAGER_A1);
        String client = newClient(token, "Vefat", "Sahip");
        String id = body(postJson(token, CLINIC, "/api/v1/animals", animal("Vefat", dogId, null, owners(client, true)))).get("id").asText();

        postJson(token, "/api/v1/animals/" + id + "/deceased", Map.of("deceasedAt", LocalDate.now().plusDays(1).toString()))
                .andExpect(status().isBadRequest());
        postJson(token, "/api/v1/animals/" + id + "/deceased", Map.of()).andExpect(status().isBadRequest());
        postJson(token, "/api/v1/animals/" + id + "/deceased", Map.of("deceasedAt", LocalDate.now().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.deceasedAt").value(LocalDate.now().toString()));
        postJson(token, "/api/v1/animals/" + id + "/deceased", Map.of("deceasedAt", LocalDate.now().toString()))
                .andExpect(status().isConflict());
    }

    private static int primaryCount(JsonNode animal) {
        int count = 0;
        for (JsonNode o : animal.get("owners")) {
            if (o.get("primaryContact").asBoolean() && o.get("validTo").isNull()) {
                count++;
            }
        }
        return count;
    }

    private static String ownerId(JsonNode animal, String clientId) {
        for (JsonNode o : animal.get("owners")) {
            if (o.get("clientId").asText().equals(clientId) && o.get("validTo").isNull()) {
                return o.get("id").asText();
            }
        }
        throw new AssertionError("owner not found");
    }

    @Test
    void clientContext_createLinksPrimaryOwner_andClientListShowsAnimals() throws Exception {
        String token = token(ORG_A_CODE, EMAIL_MANAGER_A1);
        String client = newClient(token, "Listeci", "Sahip");

        for (String name : List.of("Zeytin-IT", "Badem-IT")) {
            Map<String, Object> req = new HashMap<>();
            req.put("name", name);
            req.put("speciesId", dogId);
            JsonNode a = body(postJson(token, CLINIC, "/api/v1/clients/" + client + "/animals", req).andExpect(status().isCreated()));
            assertThat(a.get("primaryOwnerId").asText()).isEqualTo(client);
            assertThat(a.get("owners")).hasSize(1);
        }

        getJson(token, "/api/v1/clients?q=badem-it").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(client))
                .andExpect(jsonPath("$.content[0].animalCount").value(2))
                .andExpect(jsonPath("$.content[0].animals.length()").value(2))
                .andExpect(jsonPath("$.content[0].animals[0].name").value("Badem-IT"))
                .andExpect(jsonPath("$.content[0].animals[0].isPrimaryOwner").value(true))
                .andExpect(jsonPath("$.content[0].matchedAnimalIds.length()").value(1));
    }

    @Test
    void clientContext_inactiveClientIs422() throws Exception {
        String token = token(ORG_A_CODE, EMAIL_MANAGER_A1);
        String client = newClient(token, "Pasif", "Hayvansiz");
        postJson(token, CLINIC, "/api/v1/clients/" + client + "/deactivate", Map.of()).andExpect(status().is2xxSuccessful());

        Map<String, Object> req = new HashMap<>();
        req.put("name", "Olmaz");
        req.put("speciesId", dogId);
        postJson(token, CLINIC, "/api/v1/clients/" + client + "/animals", req).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void byChip_findsAnimalWithOwners_and404WhenUnknown() throws Exception {
        String token = token(ORG_A_CODE, EMAIL_MANAGER_A1);
        String client = newClient(token, "Cipli", "Sahip");
        Map<String, Object> req = new HashMap<>();
        req.put("name", "Cipli-IT");
        req.put("speciesId", dogId);
        JsonNode a = body(postJson(token, CLINIC, "/api/v1/clients/" + client + "/animals", req).andExpect(status().isCreated()));
        Map<String, Object> id = Map.of("identifierType", "MICROCHIP", "identifierValue", "900123456789012");
        postJson(token, CLINIC, "/api/v1/animals/" + a.get("id").asText() + "/identifiers", id).andExpect(status().isCreated());

        getJson(token, "/api/v1/animals/by-chip/900-123456789012").andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(a.get("id").asText()))
                .andExpect(jsonPath("$[0].primaryOwnerId").value(client));
        getJson(token, "/api/v1/animals/by-chip/000000000000000").andExpect(status().isNotFound());
    }

    private String newClient(String token, String first, String last) throws Exception {
        return body(postJson(token, CLINIC, "/api/v1/clients",
                Map.of("clientType", "PERSON", "firstName", first, "lastName", last)).andExpect(status().isCreated())).get("id").asText();
    }

    private static Map<String, Object> owner(String clientId, boolean primary) {
        Map<String, Object> owner = new HashMap<>();
        owner.put("clientId", clientId);
        owner.put("relationshipType", "OWNER");
        owner.put("isPrimaryContact", primary);
        return owner;
    }

    private static List<Map<String, Object>> owners(String clientId, boolean primary) {
        return List.of(owner(clientId, primary));
    }

    private static Map<String, Object> animal(String name, UUID speciesId, UUID breedId, List<Map<String, Object>> owners) {
        Map<String, Object> request = new HashMap<>();
        request.put("name", name);
        request.put("speciesId", speciesId);
        if (breedId != null) {
            request.put("breedId", breedId);
        }
        request.put("owners", owners);
        return request;
    }

    private static UUID species(Connection c, String code) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("select id from vet.species where code = ?")) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getObject(1, UUID.class);
            }
        }
    }

    private static UUID breed(Connection c, UUID speciesId, String name) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("insert into vet.breeds (species_id, name) values (?, ?) returning id")) {
            ps.setObject(1, speciesId);
            ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getObject(1, UUID.class);
            }
        }
    }
}
