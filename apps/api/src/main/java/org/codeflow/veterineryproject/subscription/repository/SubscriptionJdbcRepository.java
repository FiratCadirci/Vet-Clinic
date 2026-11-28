package org.codeflow.veterineryproject.subscription.repository;

import org.codeflow.veterineryproject.subscription.dto.SubscriptionDto.BillingEventResponse;
import org.codeflow.veterineryproject.subscription.dto.SubscriptionDto.SubscriptionView;
import org.codeflow.veterineryproject.subscription.model.BillingPeriod;
import org.codeflow.veterineryproject.subscription.model.SubscriptionStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Kliniğin aboneliği ve simüle ödemeleri. Sorgular kiracı bağlamında (RLS) çalışır; organizasyon kimliği yine de
 * açıkça verilir. "Güncel" abonelik TRIAL/ACTIVE/PAST_DUE'dur (tekil indeks); yoksa en son (iptal edilmiş) satır.
 */
@Repository
public class SubscriptionJdbcRepository {

    /** Güncel satır önce, sonra en yenisi. */
    private static final String LATEST_ORDER = """
            order by (s.status in ('TRIAL', 'ACTIVE', 'PAST_DUE')) desc, s.created_at desc
            limit 1
            """;

    private static final RowMapper<BillingEventResponse> EVENT = (rs, i) -> new BillingEventResponse(
            rs.getObject("id", UUID.class), rs.getString("event_no"), rs.getBigDecimal("amount"),
            rs.getString("currency"), rs.getString("status"), rs.getString("payment_mode"),
            rs.getObject("billing_period_start", OffsetDateTime.class),
            rs.getObject("billing_period_end", OffsetDateTime.class),
            rs.getObject("processed_at", OffsetDateTime.class));

    /** Kilitli abonelik satırında iş kuralları için gereken alanlar. */
    public record LockedSubscription(UUID id, SubscriptionStatus status, BillingPeriod billingPeriod, BigDecimal price,
                                     String currency, OffsetDateTime currentPeriodStart, OffsetDateTime currentPeriodEnd,
                                     OffsetDateTime cancelledAt) {
        public boolean cancelRequested() {
            return cancelledAt != null;
        }
    }

    /** Paket kataloğu satırı (aboneliğe kopyalanan alanlar). */
    public record PlanTerms(UUID id, String name, BillingPeriod billingPeriod, BigDecimal price, String currency,
                            int maxBranches, int maxUsers) {
    }

    private final JdbcTemplate jdbc;

    public SubscriptionJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<SubscriptionView> findLatestView(UUID organizationId) {
        return jdbc.query("""
                select s.id, s.plan_id, p.code as plan_code, p.name as plan_name, s.status, s.billing_period,
                       s.price_snapshot, s.currency, s.starts_at, s.current_period_start, s.current_period_end,
                       s.next_renewal_at, s.cancelled_at, s.max_branches_snapshot, s.max_users_snapshot
                from vet.organization_subscriptions s
                join vet.subscription_plans p on p.id = s.plan_id
                where s.organization_id = ?
                """ + LATEST_ORDER, (rs, i) -> {
            SubscriptionStatus status = SubscriptionStatus.valueOf(rs.getString("status"));
            OffsetDateTime cancelledAt = rs.getObject("cancelled_at", OffsetDateTime.class);
            return new SubscriptionView(rs.getObject("id", UUID.class), rs.getObject("plan_id", UUID.class),
                    rs.getString("plan_code"), rs.getString("plan_name"), status.name(), rs.getString("billing_period"),
                    rs.getBigDecimal("price_snapshot"), rs.getString("currency"),
                    rs.getObject("starts_at", OffsetDateTime.class),
                    rs.getObject("current_period_start", OffsetDateTime.class),
                    rs.getObject("current_period_end", OffsetDateTime.class),
                    rs.getObject("next_renewal_at", OffsetDateTime.class),
                    cancelledAt, cancelledAt != null && status.isRunning(),
                    rs.getInt("max_branches_snapshot"), rs.getInt("max_users_snapshot"));
        }, organizationId).stream().findFirst();
    }

    /** Ödeme, iptal ve paket değişikliği aynı abonelik satırında sıraya girer. */
    public Optional<LockedSubscription> lockLatest(UUID organizationId) {
        return jdbc.query("""
                select s.id, s.status, s.billing_period, s.price_snapshot, s.currency, s.current_period_start,
                       s.current_period_end, s.cancelled_at
                from vet.organization_subscriptions s
                where s.organization_id = ?
                """ + LATEST_ORDER + " for update", (rs, i) -> new LockedSubscription(
                rs.getObject("id", UUID.class), SubscriptionStatus.valueOf(rs.getString("status")),
                BillingPeriod.valueOf(rs.getString("billing_period")), rs.getBigDecimal("price_snapshot"),
                rs.getString("currency"), rs.getObject("current_period_start", OffsetDateTime.class),
                rs.getObject("current_period_end", OffsetDateTime.class),
                rs.getObject("cancelled_at", OffsetDateTime.class)), organizationId).stream().findFirst();
    }

    /**
     * Organizasyon satırını kilitler (V23 limit kontrolleriyle aynı kilit sırası): paket düşürülürken eşzamanlı şube
     * açma veya davet kabulü kullanımı değiştiremez.
     */
    public void lockOrganization(UUID organizationId) {
        jdbc.query("select id from vet.organizations where id = ? for update", (rs, i) -> rs.getObject(1, UUID.class),
                organizationId);
    }

    public Optional<PlanTerms> findActivePlan(UUID planId) {
        return jdbc.query("""
                select id, name, billing_period, price, currency, max_branches, max_users
                from vet.subscription_plans
                where id = ? and is_active
                """, (rs, i) -> new PlanTerms(rs.getObject("id", UUID.class), rs.getString("name"),
                BillingPeriod.valueOf(rs.getString("billing_period")), rs.getBigDecimal("price"),
                rs.getString("currency"), rs.getInt("max_branches"), rs.getInt("max_users")), planId)
                .stream().findFirst();
    }

    /** Paket ve dönem değişir; fiyat ve limitler yeni paketten kopyalanır. Dönem sonu (yenileme tarihi) değişmez. */
    public void changePlan(UUID subscriptionId, PlanTerms plan) {
        jdbc.update("""
                update vet.organization_subscriptions
                set plan_id = ?, billing_period = ?, price_snapshot = ?, currency = ?,
                    max_branches_snapshot = ?, max_users_snapshot = ?, updated_at = now()
                where id = ?
                """, plan.id(), plan.billingPeriod().name(), plan.price(), plan.currency(), plan.maxBranches(),
                plan.maxUsers(), subscriptionId);
    }

    /** Organizasyon başına sıralı ödeme numarası (ABN-000001); abonelik satırı kilitli olduğu için çakışmaz. */
    public BillingEventResponse insertSimulatedPayment(UUID organizationId, UUID subscriptionId, BigDecimal amount,
                                                       String currency, OffsetDateTime periodStart,
                                                       OffsetDateTime periodEnd, OffsetDateTime processedAt) {
        return jdbc.queryForObject("""
                insert into vet.subscription_billing_events (organization_id, subscription_id, event_no, amount, currency,
                    status, payment_mode, billing_period_start, billing_period_end, processed_at, provider_reference)
                values (?, ?, 'ABN-' || lpad((select count(*) + 1 from vet.subscription_billing_events e
                                               where e.organization_id = ?)::text, 6, '0'),
                        ?, ?, 'SUCCEEDED', 'SIMULATED', ?, ?, ?, 'SIMULATION')
                returning id, event_no, amount, currency, status, payment_mode, billing_period_start, billing_period_end,
                          processed_at
                """, EVENT, organizationId, subscriptionId, organizationId, amount, currency, periodStart, periodEnd,
                processedAt);
    }

    /** Ödeme sonrası: aktif, iptal isteği kalkar, yenileme tarihi yeni dönem sonudur. */
    public void markPaid(UUID subscriptionId, OffsetDateTime periodStart, OffsetDateTime periodEnd) {
        jdbc.update("""
                update vet.organization_subscriptions
                set status = 'ACTIVE', current_period_start = ?, current_period_end = ?, next_renewal_at = ?,
                    cancelled_at = null, updated_at = now()
                where id = ?
                """, periodStart, periodEnd, periodEnd, subscriptionId);
    }

    /** Dönem sonunda iptal: durum dönem bitene kadar korunur, yenileme olmaz. */
    public void requestCancellation(UUID subscriptionId) {
        jdbc.update("""
                update vet.organization_subscriptions
                set cancelled_at = now(), next_renewal_at = null, updated_at = now()
                where id = ?
                """, subscriptionId);
    }

    /** Dönemi zaten bitmiş (gecikmiş) abonelik hemen iptal olur. */
    public void cancelNow(UUID subscriptionId) {
        jdbc.update("""
                update vet.organization_subscriptions
                set status = 'CANCELLED', cancelled_at = now(), next_renewal_at = null, updated_at = now()
                where id = ?
                """, subscriptionId);
    }

    public List<BillingEventResponse> listBillingEvents(UUID organizationId) {
        return jdbc.query("""
                select id, event_no, amount, currency, status, payment_mode, billing_period_start, billing_period_end,
                       processed_at
                from vet.subscription_billing_events
                where organization_id = ?
                order by coalesce(processed_at, created_at) desc, event_no desc
                """, EVENT, organizationId);
    }

    /** Oturumsuz (SECURITY DEFINER): dönemi biten deneme/aktif aboneliği olan organizasyonlar. */
    public List<UUID> findOrganizationsWithEndedPeriods() {
        return jdbc.queryForList("select * from vet.subscription_due_organizations()", UUID.class);
    }

    /**
     * Kiracı bağlamında: dönemi biten deneme/aktif abonelik gecikmiş (PAST_DUE) olur; iptal istenmişse CANCELLED.
     * Koşul satırın durumunu da kontrol ettiği için eşzamanlı iki koşu aynı satırı iki kez işlemez.
     */
    public List<SubscriptionStatus> closeEndedPeriods(UUID organizationId) {
        return jdbc.query("""
                update vet.organization_subscriptions
                set status = case when cancelled_at is null then 'PAST_DUE' else 'CANCELLED' end, updated_at = now()
                where organization_id = ? and status in ('TRIAL', 'ACTIVE') and current_period_end <= now()
                returning status
                """, (rs, i) -> SubscriptionStatus.valueOf(rs.getString(1)), organizationId);
    }
}
