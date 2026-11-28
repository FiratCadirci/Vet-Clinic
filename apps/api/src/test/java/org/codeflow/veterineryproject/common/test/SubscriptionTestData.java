package org.codeflow.veterineryproject.common.test;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Abonelik ve platform paneli testlerinin verisi. Organizasyonlar {@link OrgApiTestSupport#signup} ile açılır
 * (ITORG öneki, OrgTestData.cleanup siler). Buradaki öz veri: paketler {@link #PLAN_PREFIX}, ayrı bir platform
 * yöneticisi ({@link #PLATFORM_ADMIN}) ve onun açtığı komisyon kuralları; {@link #cleanup} hepsini siler.
 */
public final class SubscriptionTestData {

    public static final String PLAN_PREFIX = "it-sub-";
    public static final UUID PLATFORM_ADMIN = UUID.fromString("5b5b0000-0000-4000-8000-000000000001");
    public static final String PLATFORM_EMAIL = "platform-sub@itsub.test";
    public static final String PASSWORD = OrgTestData.PASSWORD;

    private SubscriptionTestData() {
    }

    public static void seedPlatformAdmin(DataSource superUser) {
        exec(superUser, "INSERT INTO vet.platform_admins (id, email, password_hash, display_name, is_active) "
                + "VALUES (?, ?, vet.crypt(?, vet.gen_salt('bf', 10)), 'Abonelik Test Yöneticisi', true) "
                + "ON CONFLICT (id) DO NOTHING", PLATFORM_ADMIN, PLATFORM_EMAIL, PASSWORD);
    }

    /** Testlere özel paket; kod tekil olsun diye rastgele son ek alır. */
    public static String createPlan(DataSource superUser, String billingPeriod, String price, int maxBranches, int maxUsers) {
        String code = PLAN_PREFIX + UUID.randomUUID().toString().substring(0, 8);
        exec(superUser, "INSERT INTO vet.subscription_plans (code, name, billing_period, price, max_branches, max_users) "
                        + "VALUES (?, ?, ?, ?, ?, ?)", code, "IT Abonelik " + code, billingPeriod, new BigDecimal(price),
                maxBranches, maxUsers);
        return code;
    }

    public static UUID planId(DataSource superUser, String code) {
        return OrgTestData.queryUuid(superUser, "SELECT id FROM vet.subscription_plans WHERE code = ?", code);
    }

    public static UUID subscriptionId(DataSource superUser, UUID organizationId) {
        return OrgTestData.queryUuid(superUser, "SELECT id FROM vet.organization_subscriptions WHERE organization_id = ? "
                + "ORDER BY created_at DESC LIMIT 1", organizationId);
    }

    public static String subscriptionStatus(DataSource superUser, UUID organizationId) {
        return queryString(superUser, "SELECT status FROM vet.organization_subscriptions WHERE organization_id = ? "
                + "ORDER BY created_at DESC LIMIT 1", organizationId);
    }

    public static OffsetDateTime periodEnd(DataSource superUser, UUID organizationId) {
        return queryTimestamp(superUser, "SELECT current_period_end FROM vet.organization_subscriptions "
                + "WHERE organization_id = ? ORDER BY created_at DESC LIMIT 1", organizationId);
    }

    /** Aboneliğin dönemini geçmişe çeker (dönem başı 30 gün önce, sonu 1 saat önce). */
    public static void endPeriod(DataSource superUser, UUID organizationId, boolean cancelRequested) {
        exec(superUser, "UPDATE vet.organization_subscriptions SET current_period_start = now() - interval '30 days', "
                + "current_period_end = now() - interval '1 hour', "
                + "next_renewal_at = CASE WHEN ? THEN NULL ELSE now() - interval '1 hour' END, "
                + "cancelled_at = CASE WHEN ? THEN now() - interval '2 days' ELSE NULL END "
                + "WHERE organization_id = ?", cancelRequested, cancelRequested, organizationId);
    }

    public static UUID createRule(DataSource superUser, String productType, String rate) {
        return OrgTestData.queryUuid(superUser, "INSERT INTO vet.commission_rules (code, name, product_type, rate_percent, "
                        + "valid_from, is_active, created_by_admin_id) VALUES (?, 'IT kural', ?, ?, now() + interval '60 years', "
                        + "true, ?) RETURNING id", "ITSUB-" + UUID.randomUUID().toString().substring(0, 8), productType,
                new BigDecimal(rate), PLATFORM_ADMIN);
    }

    public static void insertBillingEvent(DataSource superUser, UUID organizationId, String eventNo, String amount,
                                          String status, OffsetDateTime processedAt) {
        exec(superUser, "INSERT INTO vet.subscription_billing_events (organization_id, subscription_id, event_no, amount, "
                        + "status, payment_mode, processed_at) VALUES (?, ?, ?, ?, ?, 'SIMULATED', ?)",
                organizationId, subscriptionId(superUser, organizationId), eventNo, new BigDecimal(amount), status, processedAt);
    }

    /**
     * Komisyon kaydını fatura olmadan yazar (yalnız rapor toplamlarını sınamak için; FK'ler replica modunda atlanır).
     * Gerçek hesap fatura kesim trigger'ındadır ve satış testlerinde sınanır.
     */
    public static void insertCommission(DataSource superUser, UUID organizationId, UUID clinicId, UUID ruleId,
                                        String eligible, String commission, String status, OffsetDateTime calculatedAt) {
        try (Connection c = superUser.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                st.execute("SET LOCAL session_replication_role = replica");
            }
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO vet.commission_records (organization_id, clinic_id, "
                    + "invoice_id, invoice_line_id, commission_rule_id, eligible_amount, rate_percent, commission_amount, "
                    + "status, calculated_at) VALUES (?, ?, ?, ?, ?, ?, 2.50, ?, ?, ?)")) {
                ps.setObject(1, organizationId);
                ps.setObject(2, clinicId);
                ps.setObject(3, UUID.randomUUID());
                ps.setObject(4, UUID.randomUUID());
                ps.setObject(5, ruleId);
                ps.setBigDecimal(6, new BigDecimal(eligible));
                ps.setBigDecimal(7, new BigDecimal(commission));
                ps.setString(8, status);
                ps.setObject(9, calculatedAt);
                ps.executeUpdate();
            }
            c.commit();
        } catch (SQLException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public static String queryString(DataSource superUser, String sql, Object... params) {
        try (Connection c = superUser.getConnection(); PreparedStatement ps = prepare(c, sql, params);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getString(1) : null;
        } catch (SQLException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public static OffsetDateTime queryTimestamp(DataSource superUser, String sql, Object... params) {
        try (Connection c = superUser.getConnection(); PreparedStatement ps = prepare(c, sql, params);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getObject(1, OffsetDateTime.class) : null;
        } catch (SQLException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public static void exec(DataSource superUser, String sql, Object... params) {
        OrgTestData.exec(superUser, sql, params);
    }

    /** Önce ITORG organizasyonları (abonelik, ödeme, komisyon kayıtları), sonra katalog ve platform yöneticisi. */
    public static synchronized void cleanup(DataSource superUser) {
        OrgTestData.cleanup(superUser);
        try (Connection c = superUser.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                st.execute("SET LOCAL session_replication_role = replica");
                st.execute("DELETE FROM vet.commission_records WHERE commission_rule_id IN "
                        + "(SELECT id FROM vet.commission_rules WHERE created_by_admin_id = '" + PLATFORM_ADMIN + "')");
                st.execute("DELETE FROM vet.commission_rules WHERE created_by_admin_id = '" + PLATFORM_ADMIN + "'");
                st.execute("DELETE FROM vet.subscription_plans WHERE code LIKE '" + PLAN_PREFIX + "%'");
                st.execute("DELETE FROM vet.platform_admin_access_log WHERE platform_admin_id = '" + PLATFORM_ADMIN + "'");
                st.execute("DELETE FROM vet.platform_refresh_tokens WHERE platform_admin_id = '" + PLATFORM_ADMIN + "'");
                st.execute("DELETE FROM vet.platform_admins WHERE id = '" + PLATFORM_ADMIN + "'");
            }
            c.commit();
        } catch (SQLException ex) {
            throw new IllegalStateException("Abonelik test verisi temizlenemedi: " + ex.getMessage(), ex);
        }
    }

    private static PreparedStatement prepare(Connection c, String sql, Object... params) throws SQLException {
        PreparedStatement ps = c.prepareStatement(sql);
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
        return ps;
    }
}
