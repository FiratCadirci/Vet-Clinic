package org.codeflow.veterineryproject.platform;

import com.fasterxml.jackson.databind.JsonNode;
import org.codeflow.veterineryproject.common.test.OrgApiTestSupport;
import org.codeflow.veterineryproject.common.test.OrgTestData;
import org.codeflow.veterineryproject.common.test.SubscriptionTestData;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FG-K5 platform yöneticisi paneli: klinik listesi, abonelik müdahalesi, paket ve komisyon kuralı yönetimi, gelir ve
 * komisyon raporları. Token türü ayrımı ve platform yöneticisinin tıbbi uçlara erişememesi.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PlatformPanelIT extends OrgApiTestSupport {

    @BeforeAll
    static void seed() {
        SubscriptionTestData.seedPlatformAdmin(superUserDataSource());
    }

    @AfterAll
    static void cleanUp() {
        SubscriptionTestData.cleanup(superUserDataSource());
    }

    // ------------------------------------------------------------------ token türü ve tıbbi veri

    @Test
    void platformEndpoints_requirePlatformToken_staffTokenIs401() throws Exception {
        Session admin = signup(null);

        mvc.perform(get("/api/platform/tenants")).andExpect(status().isUnauthorized());
        getJson(admin.token(), "/api/platform/tenants").andExpect(status().isUnauthorized());
        getJson(admin.token(), "/api/platform/plans").andExpect(status().isUnauthorized());
        postJson(admin.token(), admin.clinic(), "/api/platform/commission-rules",
                Map.of("productType", "FOOD", "ratePercent", 50)).andExpect(status().isUnauthorized());
        getJson(admin.token(), "/api/platform/reports/revenue").andExpect(status().isUnauthorized());
        getJson("bozuk-token", "/api/platform/tenants").andExpect(status().isUnauthorized());

        getJson(platformToken(), "/api/platform/tenants").andExpect(status().isOk());
    }

    @Test
    void platformToken_cannotReachClinicOrMedicalEndpoints() throws Exception {
        String platform = platformToken();
        for (String url : List.of("/api/v1/subscription", "/api/v1/patients", "/api/v1/encounters",
                "/api/v1/clients", "/api/v1/appointments", "/api/v1/organization")) {
            getJson(platform, url).andExpect(status().isUnauthorized());
        }
        postJson(platform, "/api/v1/subscription/pay", Map.of()).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ klinikler

    @Test
    void tenants_listPlanStatusAndUsage_withoutMedicalData() throws Exception {
        String plan = SubscriptionTestData.createPlan(su(), "MONTHLY", "1299.00", 3, 15);
        Session admin = signup(plan);
        staff(admin, "veterinarian", admin.clinicId());

        JsonNode tenant = tenant(platformToken(), admin.organizationId());
        assertThat(tenant.get("code").asInt()).isEqualTo(admin.organizationCode());
        assertThat(tenant.get("activeBranches").asInt()).isEqualTo(1);
        assertThat(tenant.get("activeUsers").asInt()).isEqualTo(2);
        assertThat(tenant.get("subscription").get("status").asText()).isEqualTo("TRIAL");
        assertThat(tenant.get("subscription").get("planCode").asText()).isEqualTo(plan);
        assertThat(tenant.get("subscription").get("maxUsers").asInt()).isEqualTo(15);
        assertThat(tenant.get("subscriptionRevenue").decimalValue()).isZero();
        assertThat(fieldNames(tenant)).containsExactlyInAnyOrder("id", "code", "name", "email", "phone", "active",
                "createdAt", "subscription", "activeBranches", "activeUsers", "subscriptionRevenue", "commissionRevenue");
    }

    @Test
    void updateSubscription_changesPlanAndSuspends_isLoggedInPlatformLog_andValidates() throws Exception {
        Session admin = signup(SubscriptionTestData.createPlan(su(), "MONTHLY", "499.00", 1, 5));
        String token = platformToken();
        UUID bigger = SubscriptionTestData.planId(su(), SubscriptionTestData.createPlan(su(), "YEARLY", "29990.00", 10, 50));
        String url = "/api/platform/tenants/" + admin.organizationId() + "/subscription";

        patchJson(token, url, Map.of("planId", bigger, "status", "ACTIVE")).andExpect(status().isOk())
                .andExpect(jsonPath("$.subscription.status").value("ACTIVE"))
                .andExpect(jsonPath("$.subscription.billingPeriod").value("YEARLY"))
                .andExpect(jsonPath("$.subscription.maxBranches").value(10))
                .andExpect(jsonPath("$.subscription.price").value(29990.00));

        // Askıya alma: gecikmiş durum; klinik ekranında da görünür ve yeni personel eklenemez.
        patchJson(token, url, Map.of("status", "PAST_DUE")).andExpect(status().isOk())
                .andExpect(jsonPath("$.subscription.status").value("PAST_DUE"));
        getJson(admin.token(), "/api/v1/subscription").andExpect(jsonPath("$.subscription.status").value("PAST_DUE"));
        patchJson(token, url, Map.of("status", "CANCELLED")).andExpect(status().isOk())
                .andExpect(jsonPath("$.subscription.cancelledAt").isNotEmpty())
                .andExpect(jsonPath("$.subscription.nextRenewalAt").doesNotExist());

        assertThat(OrgTestData.count(su(), """
                SELECT count(*) FROM vet.platform_admin_access_log
                WHERE platform_admin_id = ? AND organization_id = ? AND table_name = 'organization_subscriptions'
                  AND operation = 'UPDATE'
                """, SubscriptionTestData.PLATFORM_ADMIN, admin.organizationId())).isGreaterThanOrEqualTo(3);

        patchJson(token, url, Map.of()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        patchJson(token, url, Map.of("status", "DONDURULDU")).andExpect(status().isBadRequest());
        patchJson(token, url, Map.of("currentPeriodEnd", "2001-01-01 10:00")).andExpect(status().isUnprocessableEntity());
        patchJson(token, url, Map.of("planId", UUID.randomUUID())).andExpect(status().isNotFound());
        patchJson(token, "/api/platform/tenants/" + UUID.randomUUID() + "/subscription", Map.of("status", "ACTIVE"))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ paketler

    @Test
    void plans_crud_validation_conflicts_andPlatformLog() throws Exception {
        String token = platformToken();
        String code = SubscriptionTestData.PLAN_PREFIX + "api-" + UUID.randomUUID().toString().substring(0, 6);
        Map<String, Object> request = new HashMap<>(Map.of("code", code, "name", "Uzman", "billingPeriod", "MONTHLY",
                "price", 1999.90, "maxBranches", 4, "maxUsers", 20));

        JsonNode created = body(postJson(token, "/api/platform/plans", request).andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.price").value(1999.90))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.subscriptionCount").value(0)));
        String id = created.get("id").asText();

        postJson(token, "/api/platform/plans", request).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLAN_CODE_TAKEN"));
        postJson(token, "/api/platform/plans", Map.of("code", "Kötü Kod!", "name", "X", "billingPeriod", "WEEKLY",
                "price", -1, "maxBranches", 0, "maxUsers", 0))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.code").exists())
                .andExpect(jsonPath("$.details.fields.name").exists())
                .andExpect(jsonPath("$.details.fields.billingPeriod").exists())
                .andExpect(jsonPath("$.details.fields.price").exists())
                .andExpect(jsonPath("$.details.fields.maxBranches").exists())
                .andExpect(jsonPath("$.details.fields.maxUsers").exists());

        Map<String, Object> update = Map.of("name", "Uzman Plus", "price", 2499.00, "maxBranches", 5, "maxUsers", 25,
                "active", false);
        putJson(token, null, "/api/platform/plans/" + id, update).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Uzman Plus"))
                .andExpect(jsonPath("$.maxUsers").value(25))
                .andExpect(jsonPath("$.active").value(false));
        putJson(token, null, "/api/platform/plans/" + UUID.randomUUID(), update).andExpect(status().isNotFound());

        // Pasif paket klinik listesinde görünmez; platform listesinde görünür.
        Session admin = signup(null);
        assertThat(body(getJson(admin.token(), "/api/v1/subscription/plans")).toString()).doesNotContain(code);
        assertThat(body(getJson(token, "/api/platform/plans")).toString()).contains(code);

        assertThat(OrgTestData.count(su(), """
                SELECT count(*) FROM vet.platform_admin_access_log
                WHERE platform_admin_id = ? AND table_name = 'subscription_plans' AND row_pk = ?
                """, SubscriptionTestData.PLATFORM_ADMIN, id)).isEqualTo(2);

        // Kullanılan paket silinemez; kullanılmayan silinir.
        String used = SubscriptionTestData.createPlan(su(), "MONTHLY", "10.00", 1, 5);
        signup(used);
        delete(token, "/api/platform/plans/" + SubscriptionTestData.planId(su(), used))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PLAN_IN_USE"));
        delete(token, "/api/platform/plans/" + id).andExpect(status().isNoContent());
        delete(token, "/api/platform/plans/" + id).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ komisyon kuralları

    @Test
    void commissionRules_crud_validation_andRecordedAdmin() throws Exception {
        String token = platformToken();
        Map<String, Object> request = new HashMap<>(Map.of("productType", "FOOD", "ratePercent", 2.5,
                "validFrom", "2090-01-01 00:00"));

        JsonNode created = body(postJson(token, "/api/platform/commission-rules", request).andExpect(status().isCreated())
                .andExpect(jsonPath("$.productType").value("FOOD"))
                .andExpect(jsonPath("$.ratePercent").value(2.5))
                .andExpect(jsonPath("$.name").value("Mama komisyonu"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.inEffect").value(false)));
        String id = created.get("id").asText();
        assertThat(OrgTestData.count(su(), "SELECT count(*) FROM vet.commission_rules WHERE id = ?::uuid "
                + "AND created_by_admin_id = ?", id, SubscriptionTestData.PLATFORM_ADMIN)).isEqualTo(1);

        request.put("productType", "VACCINE");
        postJson(token, "/api/platform/commission-rules", request).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.productType").exists());
        postJson(token, "/api/platform/commission-rules", Map.of("productType", "MEDICATION", "ratePercent", 120))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.fields.ratePercent").exists());
        postJson(token, "/api/platform/commission-rules", Map.of("productType", "MEDICATION", "ratePercent", 3,
                "validFrom", "2091-01-01 00:00", "validTo", "2090-01-01 00:00"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.fields.validityOrdered").exists());

        putJson(token, null, "/api/platform/commission-rules/" + id, Map.of("ratePercent", 3.75,
                "validFrom", "2090-01-01 00:00", "validTo", "2091-01-01 00:00", "active", false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ratePercent").value(3.75))
                .andExpect(jsonPath("$.validTo").value("2091-01-01 00:00"))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.name").value("Mama komisyonu"));
        putJson(token, null, "/api/platform/commission-rules/" + UUID.randomUUID(), Map.of("ratePercent", 1,
                "validFrom", "2090-01-01 00:00", "active", true)).andExpect(status().isNotFound());
        assertThat(body(getJson(token, "/api/platform/commission-rules")).toString()).contains(id);
        assertThat(OrgTestData.count(su(), "SELECT count(*) FROM vet.platform_admin_access_log WHERE table_name = "
                + "'commission_rules' AND row_pk = ?", id)).isEqualTo(2);
    }

    /** FG-K4/K5: komisyon kuralı ve paket kataloğu klinik oturumunda DB seviyesinde de yazılamaz (V29 RLS). */
    @Test
    void clinicSession_cannotWriteCatalogTables_evenWithDirectSql() throws Exception {
        Session admin = signup(null);
        UUID rule = SubscriptionTestData.createRule(su(), "FOOD", "1.00");
        DataSource app = vetAppDataSource();

        assertThatThrownBy(() -> inTenant(app, admin, "INSERT INTO vet.commission_rules (code, name, product_type, "
                + "rate_percent) VALUES ('KLINIK-1', 'x', 'FOOD', 0)")).isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> inTenant(app, admin, "INSERT INTO vet.subscription_plans (code, name, billing_period, "
                + "price, max_branches, max_users) VALUES ('" + SubscriptionTestData.PLAN_PREFIX + "klinik', 'x', 'MONTHLY', 0, 99, 99)"))
                .isInstanceOf(SQLException.class);
        assertThat(inTenant(app, admin, "UPDATE vet.commission_rules SET rate_percent = 0 WHERE id = '" + rule + "'"))
                .isZero();
        assertThat(inTenant(app, admin, "UPDATE vet.subscription_plans SET max_users = 999")).isZero();
        assertThat(inTenant(app, admin, "DELETE FROM vet.commission_rules WHERE id = '" + rule + "'")).isZero();
        // Okuma serbesttir (fatura trigger'ı ve kayıt ekranı için).
        assertThat(OrgTestData.count(su(), "SELECT count(*) FROM vet.commission_rules WHERE id = ? AND rate_percent = 1.00",
                rule)).isEqualTo(1);
    }

    // ------------------------------------------------------------------ raporlar

    @Test
    void revenueAndCommissionReports_sumKnownDataExactly_byMonthAndClinic() throws Exception {
        Session a = signup(null);
        Session b = signup(null);
        UUID rule = SubscriptionTestData.createRule(su(), "FOOD", "2.50");
        // Abonelik ödemeleri (2019: başka test verisiyle karışmaz). İstanbul'da 1 Nisan 00:30 = UTC 31 Mart 21:30 → Nisan.
        SubscriptionTestData.insertBillingEvent(su(), a.organizationId(), "IT-1", "499.00", "SUCCEEDED", at("2019-03-05T10:00:00+03:00"));
        SubscriptionTestData.insertBillingEvent(su(), a.organizationId(), "IT-2", "4990.00", "SUCCEEDED", at("2019-03-20T10:00:00+03:00"));
        SubscriptionTestData.insertBillingEvent(su(), b.organizationId(), "IT-3", "1299.00", "SUCCEEDED", at("2019-04-10T10:00:00+03:00"));
        SubscriptionTestData.insertBillingEvent(su(), b.organizationId(), "IT-4", "1.00", "SUCCEEDED", at("2019-04-01T00:30:00+03:00"));
        SubscriptionTestData.insertBillingEvent(su(), b.organizationId(), "IT-5", "100.00", "FAILED", at("2019-04-11T10:00:00+03:00"));
        // Komisyonlar (VOID sayılmaz).
        SubscriptionTestData.insertCommission(su(), a.organizationId(), a.clinicId(), rule, "1000.00", "25.00", "PENDING", at("2019-03-10T12:00:00+03:00"));
        SubscriptionTestData.insertCommission(su(), a.organizationId(), a.clinicId(), rule, "200.00", "5.00", "APPROVED", at("2019-04-02T12:00:00+03:00"));
        SubscriptionTestData.insertCommission(su(), b.organizationId(), b.clinicId(), rule, "400.00", "10.00", "PENDING", at("2019-03-15T12:00:00+03:00"));
        SubscriptionTestData.insertCommission(su(), b.organizationId(), b.clinicId(), rule, "999.00", "24.98", "VOID", at("2019-03-16T12:00:00+03:00"));
        String token = platformToken();

        getJson(token, "/api/platform/reports/revenue?from=2019-03-01&to=2019-04-30").andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.subscription").value(6789.00))
                .andExpect(jsonPath("$.totals.commission").value(40.00))
                .andExpect(jsonPath("$.totals.total").value(6829.00))
                .andExpect(jsonPath("$.subscriptionsByMonth.length()").value(2))
                .andExpect(jsonPath("$.subscriptionsByMonth[0].month").value("2019-03"))
                .andExpect(jsonPath("$.subscriptionsByMonth[0].paymentCount").value(2))
                .andExpect(jsonPath("$.subscriptionsByMonth[0].amount").value(5489.00))
                .andExpect(jsonPath("$.subscriptionsByMonth[1].month").value("2019-04"))
                .andExpect(jsonPath("$.subscriptionsByMonth[1].paymentCount").value(2))
                .andExpect(jsonPath("$.subscriptionsByMonth[1].amount").value(1300.00))
                .andExpect(jsonPath("$.commissionsByMonth[0].month").value("2019-03"))
                .andExpect(jsonPath("$.commissionsByMonth[0].eligibleAmount").value(1400.00))
                .andExpect(jsonPath("$.commissionsByMonth[0].commissionAmount").value(35.00))
                .andExpect(jsonPath("$.commissionsByMonth[1].commissionAmount").value(5.00));
        // Bitiş günü dahil, ertesi gün (İstanbul) hariç.
        getJson(token, "/api/platform/reports/revenue?from=2019-03-01&to=2019-03-31")
                .andExpect(jsonPath("$.totals.subscription").value(5489.00))
                .andExpect(jsonPath("$.totals.commission").value(35.00));

        getJson(token, "/api/platform/reports/commissions?from=2019-03-01&to=2019-04-30").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCommission").value(40.00))
                .andExpect(jsonPath("$.totalEligible").value(1600.00))
                .andExpect(jsonPath("$.organizations.length()").value(2))
                .andExpect(jsonPath("$.organizations[0].organizationId").value(a.organizationId().toString()))
                .andExpect(jsonPath("$.organizations[0].recordCount").value(2))
                .andExpect(jsonPath("$.organizations[0].commissionAmount").value(30.00))
                .andExpect(jsonPath("$.organizations[1].organizationId").value(b.organizationId().toString()))
                .andExpect(jsonPath("$.organizations[1].commissionAmount").value(10.00));
        getJson(token, "/api/platform/reports/commissions?from=2019-03-01&to=2019-04-30&organizationId=" + b.organizationId())
                .andExpect(jsonPath("$.organizations.length()").value(1))
                .andExpect(jsonPath("$.totalCommission").value(10.00));
        getJson(token, "/api/platform/reports/commissions?from=2019-04-30&to=2019-03-01").andExpect(status().isBadRequest());

        // Klinik listesi tüm zamanların gelirini gösterir.
        JsonNode tenantA = tenant(token, a.organizationId());
        assertThat(tenantA.get("subscriptionRevenue").decimalValue()).isEqualByComparingTo("5489.00");
        assertThat(tenantA.get("commissionRevenue").decimalValue()).isEqualByComparingTo("30.00");
    }

    // ------------------------------------------------------------------ yardımcılar

    private JsonNode tenant(String token, UUID organizationId) throws Exception {
        for (JsonNode t : body(getJson(token, "/api/platform/tenants").andExpect(status().isOk()))) {
            if (t.get("id").asText().equals(organizationId.toString())) {
                return t;
            }
        }
        throw new AssertionError("Klinik listede yok: " + organizationId);
    }

    private String platformToken() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("email", SubscriptionTestData.PLATFORM_EMAIL,
                "password", SubscriptionTestData.PASSWORD));
        JsonNode response = objectMapper.readTree(mvc.perform(post("/api/platform/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        return response.get("accessToken").asText();
    }

    private static int inTenant(DataSource app, Session session, String sql) throws SQLException {
        try (Connection c = app.getConnection()) {
            c.setAutoCommit(false);
            try {
                for (String[] kv : new String[][]{{"app.organization_id", session.organizationId().toString()},
                        {"app.user_id", session.userId().toString()}, {"app.clinic_id", session.clinic()}}) {
                    try (PreparedStatement ps = c.prepareStatement("SELECT set_config(?, ?, true)")) {
                        ps.setString(1, kv[0]);
                        ps.setString(2, kv[1]);
                        ps.execute();
                    }
                }
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    return ps.executeUpdate();
                }
            } finally {
                c.rollback();
            }
        }
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        Iterator<String> it = node.fieldNames();
        it.forEachRemaining(names::add);
        return names;
    }

    private static OffsetDateTime at(String iso) {
        return OffsetDateTime.parse(iso);
    }

    private static DataSource su() {
        return superUserDataSource();
    }
}
