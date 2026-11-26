package org.codeflow.veterineryproject.common.test;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Hasta / randevu IT'leri için ortak yardımcılar: API üzerinden sahip + hasta kaydı, PATCH çağrısı. */
public abstract class PatientsApiTestSupport extends ApiTestSupport {

    private static final AtomicInteger PHONE = new AtomicInteger((int) (System.nanoTime() % 1_000_000));

    @BeforeAll
    static void seedPatientsData() {
        PatientsTestData.seed(superUserDataSource());
    }

    @AfterAll
    static void cleanUpPatientsData() {
        PatientsTestData.cleanup(superUserDataSource());
    }

    protected static String dogSpeciesId() {
        return ClinicalTestData.text(superUserDataSource(), "SELECT id FROM vet.species WHERE code = 'DOG'");
    }

    /** Benzersiz telefonla sahip oluşturur (mükerrer kontrolüne takılmaz); dönen: client id. */
    protected String newClient(String token, String clinic, String firstName, String lastName) throws Exception {
        String phone = uniquePhone();
        Map<String, Object> body = Map.of("clientType", "PERSON", "firstName", firstName, "lastName", lastName,
                "contact", Map.of("phone", phone));
        return body(postJson(token, clinic, "/api/v1/clients", body).andExpect(status().isCreated())).get("id").asText();
    }

    /** Sahip bağlamında hasta kaydı (sahip birincil iletişim olur); dönen: animal id. */
    protected String newAnimal(String token, String clinic, String clientId, String name) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("speciesId", dogSpeciesId());
        body.put("sex", "FEMALE");
        return body(postJson(token, clinic, "/api/v1/clients/" + clientId + "/animals", body)
                .andExpect(status().isCreated())).get("id").asText();
    }

    protected ResultActions patch(String token, String clinic, String url, Object body) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.patch(url).header("Authorization", "Bearer " + token)
                .header("X-Clinic-Id", clinic).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    protected JsonNode json(ResultActions actions) throws Exception {
        return body(actions);
    }

    protected static String uniquePhone() {
        return "53" + String.format("%08d", Math.floorMod(PHONE.incrementAndGet(), 100_000_000));
    }

    protected static UUID uuid(String value) {
        return UUID.fromString(value);
    }
}
