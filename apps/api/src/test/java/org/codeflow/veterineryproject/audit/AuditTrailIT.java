package org.codeflow.veterineryproject.audit;

import com.fasterxml.jackson.databind.JsonNode;
import org.codeflow.veterineryproject.common.test.ApiTestSupport;
import org.codeflow.veterineryproject.common.test.ReportTestData;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.codeflow.veterineryproject.common.test.ClinicalTestData.count;
import static org.codeflow.veterineryproject.common.test.ReportTestData.*;
import static org.codeflow.veterineryproject.common.test.TenantTestData.uuid;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Denetim izi: FG-L2 (kritik değişiklikler, filtre, kullanıcı adı, 500 sınırı – FOG-8), FG-J4 / FOG-13
 * (hasta profili, sahip, muayene görüntülemesi denetim izine düşer; 403/404 düşmez), şube ve organizasyon kapsamı.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuditTrailIT extends ApiTestSupport {

    private static final String AUDIT = "/api/v1/audit-logs";
    private static final String MARCH = "from=2026-02-28T21:00:00Z&to=2026-03-31T21:00:00Z";
    private static final UUID APPOINTMENT_ENTITY = uuid(0x310401);
    private static final UUID INVOICE_ENTITY = uuid(0x310402);

    @BeforeAll
    static void seed() {
        ReportTestData.seed(superUserDataSource());
        var su = superUserDataSource();
        audit(su, ORG_R, R2, MANAGER_R2, "APPOINTMENT_CREATE", "APPOINTMENT", APPOINTMENT_ENTITY, at("2026-03-05 10:00"));
        audit(su, ORG_R, R1, ADMIN, "INVOICE_ISSUE", "INVOICE", INVOICE_ENTITY, at("2026-03-06 11:00"));
        audit(su, ORG_R, R3, ADMIN, "PRODUCT_UPDATE", "PRODUCT", PRODUCT_AMOX, at("2026-03-07 12:00"));
        audit(su, ORG_R, null, ADMIN, "ORGANIZATION_UPDATE", "organization", ORG_R, at("2026-03-08 13:00"));
        audit(su, ORG_Q, Q1, ADMIN_Q, "INVOICE_ISSUE", "INVOICE", INVOICE_ENTITY, at("2026-03-06 11:00"));
        bulkAudit(su, ORG_R, R1, ADMIN, "BULK_TEST", 510, at("2026-02-01 09:00"));
    }

    @AfterAll
    static void cleanup() {
        ReportTestData.cleanup(superUserDataSource());
    }

    private String admin() throws Exception {
        return token(ORG_R_CODE, EMAIL_ADMIN);
    }

    private JsonNode logs(String token, String query) throws Exception {
        return body(getJson(token, AUDIT + "?" + query).andExpect(status().isOk()));
    }

    // ------------------------------------------------------------------ FG-L2 filtreler

    @Test
    void filters_byActionEntityUserEntityIdAndDate_withUserAndBranchNames() throws Exception {
        String t = admin();
        JsonNode byAction = logs(t, "action=appointment_create");
        assertThat(byAction).hasSize(1);
        JsonNode row = byAction.get(0);
        assertThat(row.get("entityType").asText()).isEqualTo("APPOINTMENT");
        assertThat(row.get("entityId").asText()).isEqualTo(APPOINTMENT_ENTITY.toString());
        assertThat(row.get("userName").asText()).isEqualTo(NAME_MANAGER_R2);
        assertThat(row.get("actorUserId").asText()).isEqualTo(MANAGER_R2.toString());
        assertThat(row.get("clinicName").asText()).isEqualTo("R-Kadıköy");
        assertThat(row.get("occurredAt").asText()).isEqualTo("2026-03-05 10:00");

        // Kayıt türü büyük/küçük harf duyarsız; aynı kimlik OrgQ'da da var ama görünmez.
        assertThat(actions(logs(t, "entityType=invoice&" + MARCH))).containsExactly("INVOICE_ISSUE");
        assertThat(actions(logs(t, "entityId=" + INVOICE_ENTITY))).containsExactly("INVOICE_ISSUE");
        assertThat(actions(logs(t, "userId=" + MANAGER_R2))).containsExactly("APPOINTMENT_CREATE");
        // Tarih aralığı: Mart'taki dört kayıt, en yeniden eskiye; Şubat'taki toplu kayıtlar dışarıda.
        assertThat(actions(logs(t, MARCH))).containsExactly("ORGANIZATION_UPDATE", "PRODUCT_UPDATE", "INVOICE_ISSUE",
                "APPOINTMENT_CREATE");
        assertThat(actions(logs(t, "from=2026-03-06T00:00:00Z&to=2026-03-07T00:00:00Z"))).containsExactly("INVOICE_ISSUE");
    }

    @Test
    void branchUser_seesOnlyOwnBranchAndOrganizationLevelEntries() throws Exception {
        assertThat(actions(logs(token(ORG_R_CODE, EMAIL_MANAGER_R2), MARCH)))
                .containsExactly("ORGANIZATION_UPDATE", "APPOINTMENT_CREATE");
        assertThat(actions(logs(token(ORG_Q_CODE, EMAIL_ADMIN_Q), MARCH))).containsExactly("INVOICE_ISSUE");
    }

    @Test
    void limit_defaultsTo100_maxIs500() throws Exception {
        String t = admin();
        assertThat(logs(t, "action=BULK_TEST")).hasSize(100);
        JsonNode max = logs(t, "action=BULK_TEST&limit=500");
        assertThat(max).hasSize(500);
        assertThat(max.get(0).get("id").asLong()).isGreaterThan(max.get(499).get("id").asLong());
        getJson(t, AUDIT + "?limit=501").andExpect(status().isBadRequest());
        getJson(t, AUDIT + "?limit=0").andExpect(status().isBadRequest());
        getJson(t, AUDIT + "?entityId=abc").andExpect(status().isBadRequest());
        getJson(t, AUDIT + "?from=2026-03-10T00:00:00Z&to=2026-03-01T00:00:00Z").andExpect(status().isBadRequest());
    }

    @Test
    void unauthorizedRoles_forbidden() throws Exception {
        getJson(token(ORG_R_CODE, EMAIL_RECEPTION), AUDIT).andExpect(status().isForbidden());
    }

    @Test
    void criticalChange_viaApi_appearsInTrail() throws Exception {
        String t = admin();
        JsonNode product = body(postJson(t, R1.toString(), "/api/v1/products", Map.of("name", "Denetim ürünü",
                "productType", "CONSUMABLE", "baseUnit", "adet", "tracksLot", false, "minimumStock", 0,
                "salePrice", "10.00", "taxRate", 20)).andExpect(status().isCreated()));
        JsonNode rows = logs(t, "action=PRODUCT_CREATE&entityId=" + product.get("id").asText());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("userName").asText()).isEqualTo(NAME_ADMIN);
        assertThat(rows.get(0).get("clinicId").asText()).isEqualTo(R1.toString());
    }

    // ------------------------------------------------------------------ FG-J4 görüntüleme

    @Test
    void viewingPatientClientAndEncounter_isWrittenToTrail() throws Exception {
        String t = admin();
        getJson(t, R1.toString(), "/api/v1/patients/" + PET1).andExpect(status().isOk());
        getJson(t, R1.toString(), "/api/v1/clients/" + CLIENT1).andExpect(status().isOk());
        getJson(t, R1.toString(), "/api/v1/encounters/" + ENC_R1_CONSULT).andExpect(status().isOk());

        JsonNode view = logs(t, "action=ANIMAL_VIEW&entityId=" + PET1);
        assertThat(view).hasSize(1);
        assertThat(view.get(0).get("entityType").asText()).isEqualTo("ANIMAL");
        assertThat(view.get(0).get("userName").asText()).isEqualTo(NAME_ADMIN);
        assertThat(view.get(0).get("clinicName").asText()).isEqualTo("R-Merkez");
        assertThat(view.get(0).get("ipAddress").asText()).isEqualTo("127.0.0.1");
        assertThat(logs(t, "action=CLIENT_VIEW&entityId=" + CLIENT1)).hasSize(1);
        assertThat(logs(t, "action=ENCOUNTER_VIEW&entityId=" + ENC_R1_CONSULT)).hasSize(1);
        // Yalnız kim/ne/hangi kayıt: eski/yeni değer veya içerik yazılmaz.
        assertThat(count(superUserDataSource(), "SELECT count(*) FROM vet.audit_logs WHERE organization_id = ? "
                + "AND action LIKE '%_VIEW' AND (old_values IS NOT NULL OR new_values IS NOT NULL)", ORG_R)).isZero();
    }

    @Test
    void failedViews_notFoundOrForbidden_areNotWritten() throws Exception {
        getJson(admin(), R1.toString(), "/api/v1/patients/" + PET_Q).andExpect(status().isNotFound());
        assertThat(count(superUserDataSource(), "SELECT count(*) FROM vet.audit_logs WHERE entity_id = ?", PET_Q)).isZero();

        getJson(token(ORG_R_CODE, EMAIL_RECEPTION), R1.toString(), "/api/v1/encounters/" + ENC_R1_VACCINE)
                .andExpect(status().isForbidden());
        assertThat(count(superUserDataSource(), "SELECT count(*) FROM vet.audit_logs WHERE entity_id = ? AND actor_user_id = ?",
                ENC_R1_VACCINE, RECEPTION)).isZero();
    }

    private static List<String> actions(JsonNode rows) {
        List<String> out = new ArrayList<>();
        rows.forEach(r -> out.add(r.get("action").asText()));
        return out;
    }
}
