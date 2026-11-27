package org.codeflow.veterineryproject.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import org.codeflow.veterineryproject.common.test.OrgApiTestSupport;
import org.codeflow.veterineryproject.common.test.OrgTestData;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Klinik hizmetleri kataloğu: services.manage ile yazma, tüm personelle okuma, organizasyon izolasyonu. */
@SpringBootTest
@AutoConfigureMockMvc
class ServiceCatalogIT extends OrgApiTestSupport {

    private static Map<String, Object> service(String name, int minutes, String price, String tax) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("durationMinutes", minutes);
        body.put("price", price);
        body.put("taxRate", tax);
        return body;
    }

    @Test
    void crud_withPermissionChecks() throws Exception {
        Session admin = signup(OrgTestData.createPlan(superUserDataSource(), 2, 10));
        Session manager = staff(admin, "branch_manager", admin.clinicId());
        Session reception = staff(admin, "receptionist", admin.clinicId());

        JsonNode created = body(postJson(admin.token(), admin.clinic(), "/api/v1/services", service("Genel muayene", 30, "750.00", "20"))
                .andExpect(status().isCreated()));
        String id = created.get("id").asText();
        assertThat(created.get("code").asText()).isEqualTo("GENEL-MUAYENE");
        assertThat(created.get("price").decimalValue()).isEqualByComparingTo("750.00");
        assertThat(created.get("active").asBoolean()).isTrue();

        postJson(admin.token(), admin.clinic(), "/api/v1/services", service("Genel Muayene", 20, "100", "20"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_SERVICE"));
        postJson(reception.token(), reception.clinic(), "/api/v1/services", service("Tırnak kesimi", 10, "100", "20"))
                .andExpect(status().isForbidden());
        getJson(reception.token(), "/api/v1/services").andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(id));

        Map<String, Object> update = service("Genel muayene", 45, "800.50", "10");
        update.put("active", false);
        putJson(manager.token(), manager.clinic(), "/api/v1/services/" + id, update).andExpect(status().isOk())
                .andExpect(jsonPath("$.durationMinutes").value(45))
                .andExpect(jsonPath("$.taxRate").value(10.0))
                .andExpect(jsonPath("$.active").value(false));
        assertThat(body(getJson(reception.token(), "/api/v1/services"))).isEmpty();
        assertThat(body(getJson(reception.token(), "/api/v1/services?includeInactive=true"))).hasSize(1);
        putJson(reception.token(), reception.clinic(), "/api/v1/services/" + id, update).andExpect(status().isForbidden());

        deleteJson(reception.token(), reception.clinic(), "/api/v1/services/" + id).andExpect(status().isForbidden());
        deleteJson(admin.token(), admin.clinic(), "/api/v1/services/" + id).andExpect(status().isNoContent());
        getJson(admin.token(), "/api/v1/services/" + id).andExpect(status().isNotFound());
    }

    @Test
    void validationIs400_otherOrganizationIs404() throws Exception {
        Session admin = signup(null);
        Session other = signup(null);
        postJson(admin.token(), admin.clinic(), "/api/v1/services", service("Aşı uygulaması", 2, "100", "20"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.fields.durationMinutes").exists());
        postJson(admin.token(), admin.clinic(), "/api/v1/services", service("Aşı uygulaması", 15, "-1", "150"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.fields.price").exists())
                .andExpect(jsonPath("$.details.fields.taxRate").exists());

        String id = body(postJson(admin.token(), admin.clinic(), "/api/v1/services", service("Aşı uygulaması", 15, "250", "20"))
                .andExpect(status().isCreated())).get("id").asText();
        getJson(other.token(), "/api/v1/services/" + id).andExpect(status().isNotFound());
        putJson(other.token(), other.clinic(), "/api/v1/services/" + id, service("Ele geçir", 15, "1", "20")).andExpect(status().isNotFound());
        deleteJson(other.token(), other.clinic(), "/api/v1/services/" + id).andExpect(status().isNotFound());
        assertThat(body(getJson(other.token(), "/api/v1/services"))).isEmpty();
    }
}
