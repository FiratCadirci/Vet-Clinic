package org.codeflow.veterineryproject.subscription;

import com.fasterxml.jackson.databind.JsonNode;
import org.codeflow.veterineryproject.common.test.OrgApiTestSupport;
import org.codeflow.veterineryproject.common.test.OrgTestData;
import org.codeflow.veterineryproject.common.test.SubscriptionTestData;
import org.codeflow.veterineryproject.common.time.ApiDateTime;
import org.codeflow.veterineryproject.subscription.dto.SubscriptionDto.RenewalRunResponse;
import org.codeflow.veterineryproject.subscription.service.SubscriptionRenewalJob;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;

import javax.sql.DataSource;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Klinik abonelik ekranı: FG-K1 (limit + kullanım), FG-K2 (paket düşürme 409), FG-K3 (simüle ödeme, past_due görevi). */
@SpringBootTest
@AutoConfigureMockMvc
class SubscriptionIT extends OrgApiTestSupport {

    @Autowired
    private SubscriptionRenewalJob renewalJob;

    @BeforeAll
    static void seed() {
        SubscriptionTestData.seedPlatformAdmin(superUserDataSource());
    }

    @AfterAll
    static void cleanUp() {
        SubscriptionTestData.cleanup(superUserDataSource());
    }

    @Test
    void current_showsPlanPeriodRenewalLimitsAndUsage_andActivePlans() throws Exception {
        String plan = SubscriptionTestData.createPlan(su(), "MONTHLY", "499.00", 2, 3);
        Session admin = signup(plan);

        getJson(admin.token(), "/api/v1/subscription").andExpect(status().isOk())
                .andExpect(jsonPath("$.subscription.status").value("TRIAL"))
                .andExpect(jsonPath("$.subscription.planCode").value(plan))
                .andExpect(jsonPath("$.subscription.billingPeriod").value("MONTHLY"))
                .andExpect(jsonPath("$.subscription.price").value(499.00))
                .andExpect(jsonPath("$.subscription.maxBranches").value(2))
                .andExpect(jsonPath("$.subscription.maxUsers").value(3))
                .andExpect(jsonPath("$.subscription.nextRenewalAt").isNotEmpty())
                .andExpect(jsonPath("$.subscription.cancelAtPeriodEnd").value(false))
                .andExpect(jsonPath("$.usage.activeBranches").value(1))
                .andExpect(jsonPath("$.usage.activeUsers").value(1));

        JsonNode plans = body(getJson(admin.token(), "/api/v1/subscription/plans").andExpect(status().isOk()));
        assertThat(plans.toString()).contains(plan, "baslangic-yillik");
    }

    @Test
    void changePlan_downgradeBelowUsageIs409AndChangesNothing_fittingPlanAndPeriodChangeApplies() throws Exception {
        Session admin = signup(SubscriptionTestData.createPlan(su(), "MONTHLY", "1299.00", 3, 5));
        staff(admin, "receptionist", admin.clinicId());
        OffsetDateTime periodEnd = SubscriptionTestData.periodEnd(su(), admin.organizationId());
        UUID tooSmall = SubscriptionTestData.planId(su(), SubscriptionTestData.createPlan(su(), "MONTHLY", "99.00", 1, 1));
        String yearly = SubscriptionTestData.createPlan(su(), "YEARLY", "4990.00", 1, 2);

        // FG-K2: 2 aktif kullanıcı, yeni paket en fazla 1 kullanıcı.
        postJson(admin.token(), admin.clinic(), "/api/v1/subscription/change", Map.of("planId", tooSmall))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLAN_DOWNGRADE_BLOCKED"))
                .andExpect(jsonPath("$.details.activeUsers").value(2))
                .andExpect(jsonPath("$.details.maxUsers").value(1));
        getJson(admin.token(), "/api/v1/subscription").andExpect(jsonPath("$.subscription.maxUsers").value(5))
                .andExpect(jsonPath("$.subscription.price").value(1299.00));

        // Uygun paket (yıllık): paket, dönem, fiyat ve limitler değişir; yenileme tarihi değişmez.
        postJson(admin.token(), admin.clinic(), "/api/v1/subscription/change",
                Map.of("planId", SubscriptionTestData.planId(su(), yearly)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscription.planCode").value(yearly))
                .andExpect(jsonPath("$.subscription.billingPeriod").value("YEARLY"))
                .andExpect(jsonPath("$.subscription.price").value(4990.00))
                .andExpect(jsonPath("$.subscription.maxBranches").value(1))
                .andExpect(jsonPath("$.subscription.maxUsers").value(2))
                .andExpect(jsonPath("$.subscription.status").value("TRIAL"));
        assertThat(SubscriptionTestData.periodEnd(su(), admin.organizationId())).isAtSameInstantAs(periodEnd);
        assertThat(OrgTestData.count(su(), "SELECT count(*) FROM vet.audit_logs WHERE organization_id = ? "
                + "AND action = 'SUBSCRIPTION_PLAN_CHANGE'", admin.organizationId())).isEqualTo(1);

        // Yeni limit hemen geçerlidir: kullanıcı limiti (2) dolu, yeni davet kabul edilemez.
        String token = OrgTestData.insertInvitation(su(), admin.organizationId(), uniqueEmail("fazla"), "receptionist",
                admin.userId(), java.util.List.of(admin.clinicId()));
        acceptRaw(token).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PLAN_USER_LIMIT"));
    }

    @Test
    void changePlan_validation400_unknownOrInactivePlan404_missingClinicHeader400() throws Exception {
        Session admin = signup(null);
        String inactive = SubscriptionTestData.createPlan(su(), "MONTHLY", "10.00", 5, 5);
        OrgTestData.exec(su(), "UPDATE vet.subscription_plans SET is_active = false WHERE code = ?", inactive);

        postJson(admin.token(), admin.clinic(), "/api/v1/subscription/change", Map.of())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.fields.planId").value("Paket seçilmelidir."));
        postJson(admin.token(), admin.clinic(), "/api/v1/subscription/change", Map.of("planId", UUID.randomUUID()))
                .andExpect(status().isNotFound());
        postJson(admin.token(), admin.clinic(), "/api/v1/subscription/change",
                Map.of("planId", SubscriptionTestData.planId(su(), inactive))).andExpect(status().isNotFound());
        postJson(admin.token(), "/api/v1/subscription/pay", Map.of())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MISSING_HEADER"));
    }

    @Test
    void simulatedPayment_movesRenewalOnePeriodForward_andRecordsBillingEvent() throws Exception {
        Session admin = signup(SubscriptionTestData.createPlan(su(), "MONTHLY", "499.00", 1, 5));
        OffsetDateTime trialEnd = SubscriptionTestData.periodEnd(su(), admin.organizationId());
        OffsetDateTime oneMonthLater = trialEnd.atZoneSameInstant(ApiDateTime.ZONE).plusMonths(1).toOffsetDateTime();

        postJson(admin.token(), admin.clinic(), "/api/v1/subscription/pay", Map.of())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payment.eventNo").value("ABN-000001"))
                .andExpect(jsonPath("$.payment.amount").value(499.00))
                .andExpect(jsonPath("$.payment.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.payment.paymentMode").value("SIMULATED"))
                .andExpect(jsonPath("$.current.subscription.status").value("ACTIVE"))
                .andExpect(jsonPath("$.current.subscription.nextRenewalAt").value(ApiDateTime.format(oneMonthLater)));

        assertThat(SubscriptionTestData.periodEnd(su(), admin.organizationId())).isAtSameInstantAs(oneMonthLater);
        assertThat(OrgTestData.count(su(), """
                SELECT count(*) FROM vet.subscription_billing_events
                WHERE organization_id = ? AND status = 'SUCCEEDED' AND payment_mode = 'SIMULATED' AND amount = 499.00
                  AND billing_period_start = ? AND billing_period_end = ?
                """, admin.organizationId(), trialEnd, oneMonthLater)).isEqualTo(1);

        // İkinci ödeme yenilemeyi bir dönem daha ileri alır; geçmiş en yeni önce listelenir.
        postJson(admin.token(), admin.clinic(), "/api/v1/subscription/pay", Map.of()).andExpect(status().isOk())
                .andExpect(jsonPath("$.payment.eventNo").value("ABN-000002"));
        assertThat(SubscriptionTestData.periodEnd(su(), admin.organizationId()))
                .isAtSameInstantAs(oneMonthLater.atZoneSameInstant(ApiDateTime.ZONE).plusMonths(1).toOffsetDateTime());
        getJson(admin.token(), "/api/v1/subscription/payments").andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].eventNo").value("ABN-000002"))
                .andExpect(jsonPath("$[1].eventNo").value("ABN-000001"));
    }

    @Test
    void paymentAfterPastDue_startsNewPeriodFromPaymentTime() throws Exception {
        Session admin = signup(SubscriptionTestData.createPlan(su(), "YEARLY", "4990.00", 1, 5));
        SubscriptionTestData.endPeriod(su(), admin.organizationId(), false);
        renewalJob.run();
        assertThat(SubscriptionTestData.subscriptionStatus(su(), admin.organizationId())).isEqualTo("PAST_DUE");

        OffsetDateTime before = OffsetDateTime.now();
        postJson(admin.token(), admin.clinic(), "/api/v1/subscription/pay", Map.of()).andExpect(status().isOk())
                .andExpect(jsonPath("$.current.subscription.status").value("ACTIVE"));
        OffsetDateTime end = SubscriptionTestData.periodEnd(su(), admin.organizationId());
        assertThat(end).isBetween(before.plusYears(1).minusMinutes(1), OffsetDateTime.now().plusYears(1).plusMinutes(1));
    }

    @Test
    void cancel_takesEffectAtPeriodEnd_secondCancelIs409_paymentRevokesCancellation() throws Exception {
        Session admin = signup(null);

        postJson(admin.token(), admin.clinic(), "/api/v1/subscription/cancel", Map.of()).andExpect(status().isOk())
                .andExpect(jsonPath("$.subscription.status").value("TRIAL"))
                .andExpect(jsonPath("$.subscription.cancelAtPeriodEnd").value(true))
                .andExpect(jsonPath("$.subscription.nextRenewalAt").doesNotExist());
        postJson(admin.token(), admin.clinic(), "/api/v1/subscription/cancel", Map.of())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SUBSCRIPTION_ALREADY_CANCELLED"));

        // Dönem bitince günlük görev iptal edilmiş aboneliği CANCELLED yapar (past_due değil).
        SubscriptionTestData.endPeriod(su(), admin.organizationId(), true);
        renewalJob.run();
        getJson(admin.token(), "/api/v1/subscription").andExpect(jsonPath("$.subscription.status").value("CANCELLED"))
                .andExpect(jsonPath("$.subscription.cancelAtPeriodEnd").value(false));

        // Ödeme aboneliği yeniden etkinleştirir.
        postJson(admin.token(), admin.clinic(), "/api/v1/subscription/pay", Map.of()).andExpect(status().isOk())
                .andExpect(jsonPath("$.current.subscription.status").value("ACTIVE"))
                .andExpect(jsonPath("$.current.subscription.cancelledAt").doesNotExist());
    }

    @Test
    void renewalJob_marksOnlyEndedPeriodsPastDue_perOrganization_andIsIdempotent() throws Exception {
        Session ended = signup(null);
        Session running = signup(null);
        Session cancelled = signup(null);
        SubscriptionTestData.endPeriod(su(), ended.organizationId(), false);
        SubscriptionTestData.endPeriod(su(), cancelled.organizationId(), true);

        renewalJob.runDaily();

        assertThat(SubscriptionTestData.subscriptionStatus(su(), ended.organizationId())).isEqualTo("PAST_DUE");
        assertThat(SubscriptionTestData.subscriptionStatus(su(), running.organizationId())).isEqualTo("TRIAL");
        assertThat(SubscriptionTestData.subscriptionStatus(su(), cancelled.organizationId())).isEqualTo("CANCELLED");
        getJson(ended.token(), "/api/v1/subscription").andExpect(jsonPath("$.subscription.status").value("PAST_DUE"));
        getJson(running.token(), "/api/v1/subscription").andExpect(jsonPath("$.subscription.status").value("TRIAL"));

        // Gecikmiş abonelikte yeni personel eklenemez (V23 limit kontrolü).
        String invitation = OrgTestData.insertInvitation(su(), ended.organizationId(), uniqueEmail("gec"), "receptionist",
                ended.userId(), java.util.List.of(ended.clinicId()));
        acceptRaw(invitation).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SUBSCRIPTION_INACTIVE"));

        // Tekrar koşu aynı abonelikleri yeniden işlemez.
        RenewalRunResponse again = renewalJob.run();
        assertThat(again.pastDue() + again.cancelled()).isZero();
    }

    @Test
    void manualRenewalCheck_isPlatformOnly() throws Exception {
        Session admin = signup(null);
        SubscriptionTestData.endPeriod(su(), admin.organizationId(), false);

        postJson(admin.token(), admin.clinic(), "/api/platform/subscriptions/renewal-check", Map.of())
                .andExpect(status().isUnauthorized());
        assertThat(SubscriptionTestData.subscriptionStatus(su(), admin.organizationId())).isEqualTo("TRIAL");

        postJson(platformToken(), "/api/platform/subscriptions/renewal-check", Map.of()).andExpect(status().isOk())
                .andExpect(jsonPath("$.pastDue").isNumber());
        assertThat(SubscriptionTestData.subscriptionStatus(su(), admin.organizationId())).isEqualTo("PAST_DUE");
    }

    @Test
    void rolesWithoutSubscriptionPermissions_get403_onReadAndWrite() throws Exception {
        Session admin = signup(SubscriptionTestData.createPlan(su(), "MONTHLY", "100.00", 3, 10));
        Session branchManager = staff(admin, "branch_manager", admin.clinicId());
        Session receptionist = staff(admin, "receptionist", admin.clinicId());
        UUID plan = SubscriptionTestData.planId(su(), SubscriptionTestData.createPlan(su(), "MONTHLY", "200.00", 3, 10));

        for (Session s : java.util.List.of(branchManager, receptionist)) {
            postJson(s.token(), s.clinic(), "/api/v1/subscription/change", Map.of("planId", plan))
                    .andExpect(status().isForbidden());
            postJson(s.token(), s.clinic(), "/api/v1/subscription/pay", Map.of()).andExpect(status().isForbidden());
            postJson(s.token(), s.clinic(), "/api/v1/subscription/cancel", Map.of()).andExpect(status().isForbidden());
            getJson(s.token(), "/api/v1/subscription").andExpect(status().isForbidden());
        }
        assertThat(OrgTestData.count(su(), "SELECT count(*) FROM vet.subscription_billing_events WHERE organization_id = ?",
                admin.organizationId())).isZero();
        assertThat(SubscriptionTestData.subscriptionStatus(su(), admin.organizationId())).isEqualTo("TRIAL");
    }

    @Test
    void organizationsSeeOnlyTheirOwnSubscriptionAndPayments() throws Exception {
        Session a = signup(SubscriptionTestData.createPlan(su(), "MONTHLY", "300.00", 2, 5));
        Session b = signup(SubscriptionTestData.createPlan(su(), "MONTHLY", "700.00", 2, 5));

        postJson(a.token(), a.clinic(), "/api/v1/subscription/pay", Map.of()).andExpect(status().isOk());

        getJson(b.token(), "/api/v1/subscription/payments").andExpect(jsonPath("$.length()").value(0));
        getJson(b.token(), "/api/v1/subscription").andExpect(jsonPath("$.subscription.price").value(700.00))
                .andExpect(jsonPath("$.subscription.status").value("TRIAL"));
        getJson(a.token(), "/api/v1/subscription/payments").andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].amount").value(300.00));
        assertThat(SubscriptionTestData.periodEnd(su(), b.organizationId()))
                .isCloseTo(OffsetDateTime.now().plusDays(14), org.assertj.core.api.Assertions.within(5, ChronoUnit.MINUTES));
    }

    private String platformToken() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("email", SubscriptionTestData.PLATFORM_EMAIL,
                "password", SubscriptionTestData.PASSWORD));
        JsonNode response = objectMapper.readTree(mvc.perform(post("/api/platform/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        return response.get("accessToken").asText();
    }

    private static DataSource su() {
        return superUserDataSource();
    }
}
