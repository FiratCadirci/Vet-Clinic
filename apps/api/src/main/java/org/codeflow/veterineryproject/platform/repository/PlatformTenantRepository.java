package org.codeflow.veterineryproject.platform.repository;

import org.codeflow.veterineryproject.platform.dto.PlatformDto.TenantResponse;
import org.codeflow.veterineryproject.platform.dto.PlatformDto.TenantSubscription;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Platform yöneticisi bağlamında (app.platform_admin_id) klinik listesi ve abonelik müdahalesi. Okumalar V4 platform
 * SELECT politikalarıyla, abonelik güncellemesi V29 platform UPDATE politikasıyla yapılır. Yalnız ticari tablolar
 * (organizasyon, abonelik, paket, ödeme, komisyon) ve kullanım sayıları okunur.
 */
@Repository
public class PlatformTenantRepository {

    private static final String TENANT_SELECT = """
            select o.id, o.code, o.name, o.email, o.phone, o.is_active, o.created_at,
                   s.id as subscription_id, s.status, s.plan_id, p.code as plan_code, p.name as plan_name,
                   s.billing_period, s.price_snapshot, s.currency, s.current_period_end, s.next_renewal_at,
                   s.cancelled_at, s.max_branches_snapshot, s.max_users_snapshot,
                   coalesce(u.active_branches, 0) as active_branches, coalesce(u.active_users, 0) as active_users,
                   coalesce((select sum(e.amount) from vet.subscription_billing_events e
                             where e.organization_id = o.id and e.status = 'SUCCEEDED'), 0) as subscription_revenue,
                   coalesce((select sum(r.commission_amount) from vet.commission_records r
                             where r.organization_id = o.id and r.status <> 'VOID'), 0) as commission_revenue
            from vet.organizations o
            left join lateral (
                select * from vet.organization_subscriptions s
                where s.organization_id = o.id
                order by (s.status in ('TRIAL', 'ACTIVE', 'PAST_DUE')) desc, s.created_at desc
                limit 1
            ) s on true
            left join vet.subscription_plans p on p.id = s.plan_id
            left join vet.platform_organization_usage() u on u.organization_id = o.id
            """;

    private static final RowMapper<TenantResponse> TENANT = (rs, i) -> {
        UUID subscriptionId = rs.getObject("subscription_id", UUID.class);
        TenantSubscription subscription = subscriptionId == null ? null : new TenantSubscription(subscriptionId,
                rs.getString("status"), rs.getObject("plan_id", UUID.class), rs.getString("plan_code"),
                rs.getString("plan_name"), rs.getString("billing_period"), rs.getBigDecimal("price_snapshot"),
                rs.getString("currency"), rs.getObject("current_period_end", OffsetDateTime.class),
                rs.getObject("next_renewal_at", OffsetDateTime.class), rs.getObject("cancelled_at", OffsetDateTime.class),
                rs.getInt("max_branches_snapshot"), rs.getInt("max_users_snapshot"));
        return new TenantResponse(rs.getObject("id", UUID.class), rs.getObject("code", Integer.class),
                rs.getString("name"), rs.getString("email"), rs.getString("phone"), rs.getBoolean("is_active"),
                rs.getObject("created_at", OffsetDateTime.class), subscription, rs.getInt("active_branches"),
                rs.getInt("active_users"), rs.getBigDecimal("subscription_revenue"),
                rs.getBigDecimal("commission_revenue"));
    };

    /** Müdahale edilecek abonelik satırı. */
    public record SubscriptionRef(UUID id, OffsetDateTime currentPeriodStart) {
    }

    private final JdbcTemplate jdbc;

    public PlatformTenantRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<TenantResponse> findAll() {
        return jdbc.query(TENANT_SELECT + " order by o.name, o.code", TENANT);
    }

    public Optional<TenantResponse> findById(UUID organizationId) {
        return jdbc.query(TENANT_SELECT + " where o.id = ?", TENANT, organizationId).stream().findFirst();
    }

    public boolean exists(UUID organizationId) {
        Integer count = jdbc.queryForObject("select count(*) from vet.organizations where id = ?", Integer.class,
                organizationId);
        return count != null && count > 0;
    }

    public Optional<SubscriptionRef> lockLatestSubscription(UUID organizationId) {
        return jdbc.query("""
                select s.id, s.current_period_start
                from vet.organization_subscriptions s
                where s.organization_id = ?
                order by (s.status in ('TRIAL', 'ACTIVE', 'PAST_DUE')) desc, s.created_at desc
                limit 1
                for update
                """, (rs, i) -> new SubscriptionRef(rs.getObject("id", UUID.class),
                rs.getObject("current_period_start", OffsetDateTime.class)), organizationId).stream().findFirst();
    }

    /** Paket ve dönem değişir; fiyat ve limitler paketten kopyalanır. */
    public void applyPlan(UUID subscriptionId, UUID planId) {
        jdbc.update("""
                update vet.organization_subscriptions s
                set plan_id = p.id, billing_period = p.billing_period, price_snapshot = p.price, currency = p.currency,
                    max_branches_snapshot = p.max_branches, max_users_snapshot = p.max_users, updated_at = now()
                from vet.subscription_plans p
                where s.id = ? and p.id = ?
                """, subscriptionId, planId);
    }

    /** Dönem sonu; iptal istenmemişse yenileme tarihi de aynı tarihe çekilir. */
    public void applyPeriodEnd(UUID subscriptionId, OffsetDateTime periodEnd) {
        jdbc.update("""
                update vet.organization_subscriptions
                set current_period_end = ?, next_renewal_at = case when cancelled_at is null then ? else null end,
                    updated_at = now()
                where id = ?
                """, periodEnd, periodEnd, subscriptionId);
    }

    /** İptal/sona erme durumları yenilemeyi kapatır; diğer durumlar iptal işaretini kaldırır. */
    public void applyStatus(UUID subscriptionId, String status, boolean terminal) {
        if (terminal) {
            jdbc.update("""
                    update vet.organization_subscriptions
                    set status = ?, cancelled_at = coalesce(cancelled_at, now()), next_renewal_at = null, updated_at = now()
                    where id = ?
                    """, status, subscriptionId);
        } else {
            jdbc.update("""
                    update vet.organization_subscriptions
                    set status = ?, cancelled_at = null, next_renewal_at = current_period_end, updated_at = now()
                    where id = ?
                    """, status, subscriptionId);
        }
    }
}
