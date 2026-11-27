package org.codeflow.veterineryproject.common.test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.UUID;

import static org.codeflow.veterineryproject.common.test.TenantTestData.*;

/**
 * Satış / fatura testleri (UUID öneki 0x28xxx). TenantTestData yapısını kullanır.
 * Roller V22 sistem rollerinin yetkileriyle kurulur: resepsiyon (billing.manage, komisyon okuyamaz), veteriner
 * hekim (billing.manage yok), klinik yöneticisi (billing.manage + commissions.read + stok).
 * SALES_A1: A1-Şube1 resepsiyon. SALES_A1S2: A1-Şube2 (aynı grup). SALES_A2: A2-Şube1 (başka grup).
 * VET_A1: A1-Şube1 hekim. ADMIN_A1: A1-Şube1 yönetici. SALES_B1: OrgB resepsiyon.
 * Platform komisyon kuralları (global): mama %5, ilaç %3, aşı %10 (aşı kuralı FG-K4 gereği uygulanmamalı).
 */
public final class BillingTestData {

    public static final UUID ROLE_RECEPTION_A = uuid(0x28001);
    public static final UUID ROLE_VET_A = uuid(0x28002);
    public static final UUID ROLE_ADMIN_A = uuid(0x28003);
    public static final UUID ROLE_RECEPTION_B = uuid(0x28004);

    public static final UUID SALES_A1 = uuid(0x28011);
    public static final UUID SALES_A1S2 = uuid(0x28012);
    public static final UUID SALES_A2 = uuid(0x28013);
    public static final UUID VET_A1 = uuid(0x28014);
    public static final UUID ADMIN_A1 = uuid(0x28015);
    public static final UUID SALES_B1 = uuid(0x28016);

    public static final UUID RULE_FOOD = uuid(0x28021);
    public static final UUID RULE_MEDICATION = uuid(0x28022);
    public static final UUID RULE_VACCINE = uuid(0x28023);
    public static final UUID SERVICE_EXAM_A = uuid(0x28031);
    public static final UUID SUBSCRIPTION_A = uuid(0x28041);

    public static final String EMAIL_SALES_A1 = "bill-sales-a1@test.com";
    public static final String EMAIL_SALES_A1S2 = "bill-sales-a1s2@test.com";
    public static final String EMAIL_SALES_A2 = "bill-sales-a2@test.com";
    public static final String EMAIL_VET_A1 = "bill-vet-a1@test.com";
    public static final String EMAIL_ADMIN_A1 = "bill-admin-a1@test.com";
    public static final String EMAIL_SALES_B1 = "bill-sales-b1@test.com";

    private static final String[] RECEPTION = {"clients.read", "clients.write", "appointments.manage", "inventory.read",
            "inventory.write", "billing.manage"};
    private static final String[] VETERINARIAN = {"clients.read", "clients.write", "appointments.manage", "clinical.read",
            "clinical.write", "clinical.delete", "diagnostics.manage", "inventory.read", "reports.read",
            "vaccine_protocols.manage"};
    private static final String[] ADMIN = {"clients.read", "clients.write", "inventory.read", "inventory.write",
            "billing.manage", "commissions.read", "reports.read"};

    private static final UUID[] USERS = {SALES_A1, SALES_A1S2, SALES_A2, VET_A1, ADMIN_A1, SALES_B1};
    private static final UUID[] ROLES = {ROLE_RECEPTION_A, ROLE_VET_A, ROLE_ADMIN_A, ROLE_RECEPTION_B};

    private static boolean seeded;
    private static Timestamp mark;

    private BillingTestData() {
    }

    public static synchronized void seed(DataSource superUser) {
        RegistrationTestData.seed(superUser);
        if (seeded) {
            return;
        }
        try (Connection c = superUser.getConnection()) {
            try (java.sql.Statement st = c.createStatement();
                 java.sql.ResultSet rs = st.executeQuery("SELECT clock_timestamp()")) {
                rs.next();
                mark = rs.getTimestamp(1);
            }
            c.setAutoCommit(false);
            role(c, ROLE_RECEPTION_A, ORG_A, "bill-reception", RECEPTION);
            role(c, ROLE_VET_A, ORG_A, "bill-vet", VETERINARIAN);
            role(c, ROLE_ADMIN_A, ORG_A, "bill-admin", ADMIN);
            role(c, ROLE_RECEPTION_B, ORG_B, "bill-reception", RECEPTION);
            user(c, SALES_A1, ORG_A, EMAIL_SALES_A1, BRANCH_A1_S1, ROLE_RECEPTION_A);
            user(c, SALES_A1S2, ORG_A, EMAIL_SALES_A1S2, BRANCH_A1_S2, ROLE_RECEPTION_A);
            user(c, SALES_A2, ORG_A, EMAIL_SALES_A2, BRANCH_A2_S1, ROLE_RECEPTION_A);
            user(c, VET_A1, ORG_A, EMAIL_VET_A1, BRANCH_A1_S1, ROLE_VET_A);
            user(c, ADMIN_A1, ORG_A, EMAIL_ADMIN_A1, BRANCH_A1_S1, ROLE_ADMIN_A);
            user(c, SALES_B1, ORG_B, EMAIL_SALES_B1, BRANCH_B1_S1, ROLE_RECEPTION_B);
            rule(c, RULE_FOOD, "IT-BILL-FOOD", "FOOD", "5");
            rule(c, RULE_MEDICATION, "IT-BILL-MED", "MEDICATION", "3");
            rule(c, RULE_VACCINE, "IT-BILL-VAC", "VACCINE", "10");
            exec(c, "INSERT INTO vet.service_catalog (id, organization_id, code, name, default_price, tax_rate) "
                    + "VALUES (?, ?, 'IT-BILL-EXAM', 'Genel muayene', 400, 20)", SERVICE_EXAM_A, ORG_A);
            c.commit();
            seeded = true;
        } catch (SQLException ex) {
            throw new IllegalStateException("Fatura test verisi yüklenemedi: " + ex.getMessage(), ex);
        }
    }

    /** Organizasyon A'ya güncel abonelik (TRIAL / ACTIVE) açar ya da durumunu değiştirir; null ise siler. */
    public static void subscriptionA(DataSource superUser, String status) {
        try (Connection c = superUser.getConnection()) {
            exec(c, "DELETE FROM vet.organization_subscriptions WHERE id = ?", SUBSCRIPTION_A);
            if (status != null) {
                exec(c, "INSERT INTO vet.organization_subscriptions (id, organization_id, plan_id, status, billing_period, "
                        + "price_snapshot, max_branches_snapshot, max_users_snapshot, current_period_end) "
                        + "SELECT ?, ?, id, ?, billing_period, price, 50, 500, now() + interval '14 days' "
                        + "FROM vet.subscription_plans ORDER BY created_at, code LIMIT 1", SUBSCRIPTION_A, ORG_A, status);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Abonelik ayarlanamadı: " + ex.getMessage(), ex);
        }
    }

    /** Komisyon, stok, ürün ve bu sınıfın kullanıcıları silinir (fatura/ödeme satırları RegistrationTestData'da). */
    public static synchronized void cleanup(DataSource superUser) {
        if (!seeded) {
            return;
        }
        try (Connection c = superUser.getConnection()) {
            c.setAutoCommit(false);
            exec(c, "SET LOCAL session_replication_role = replica");
            exec(c, "DELETE FROM vet.commission_records WHERE created_at >= ?", mark);
            exec(c, "DELETE FROM vet.commission_rules WHERE id IN (?, ?, ?)", RULE_FOOD, RULE_MEDICATION, RULE_VACCINE);
            exec(c, "DELETE FROM vet.organization_subscriptions WHERE id = ?", SUBSCRIPTION_A);
            exec(c, "DELETE FROM vet.notification_outbox WHERE created_at >= ?", mark);
            exec(c, "DELETE FROM vet.invoice_payments WHERE applied_at >= ?", mark);
            exec(c, "DELETE FROM vet.invoice_lines WHERE invoice_id IN (SELECT id FROM vet.invoices WHERE created_at >= ?)", mark);
            exec(c, "DELETE FROM vet.payments WHERE created_at >= ?", mark);
            exec(c, "DELETE FROM vet.invoices WHERE created_at >= ?", mark);
            exec(c, "DELETE FROM vet.inventory_transaction_lines WHERE created_at >= ?", mark);
            exec(c, "DELETE FROM vet.inventory_transactions WHERE created_at >= ?", mark);
            exec(c, "DELETE FROM vet.inventory_lots WHERE created_at >= ?", mark);
            exec(c, "DELETE FROM vet.inventory_locations WHERE created_at >= ?", mark);
            exec(c, "DELETE FROM vet.products WHERE created_at >= ?", mark);
            exec(c, "DELETE FROM vet.service_catalog WHERE id = ?", SERVICE_EXAM_A);
            for (UUID user : USERS) {
                exec(c, "DELETE FROM vet.audit_logs WHERE actor_user_id = ?", user);
                exec(c, "DELETE FROM vet.refresh_tokens WHERE user_id = ?", user);
                exec(c, "DELETE FROM vet.user_clinic_roles WHERE user_id = ?", user);
                exec(c, "DELETE FROM vet.app_users WHERE id = ?", user);
            }
            for (UUID role : ROLES) {
                exec(c, "DELETE FROM vet.role_permissions WHERE role_id = ?", role);
                exec(c, "DELETE FROM vet.roles WHERE id = ?", role);
            }
            c.commit();
            seeded = false;
        } catch (SQLException ex) {
            throw new IllegalStateException("Fatura test verisi temizlenemedi: " + ex.getMessage(), ex);
        }
    }

    private static void rule(Connection c, UUID id, String code, String productType, String rate) throws SQLException {
        exec(c, "INSERT INTO vet.commission_rules (id, code, name, product_type, rate_percent, valid_from) "
                + "VALUES (?, ?, ?, ?, ?::numeric, now() - interval '1 minute')", id, code, code, productType, rate);
    }

    private static void role(Connection c, UUID id, UUID org, String code, String... permissions) throws SQLException {
        exec(c, "INSERT INTO vet.roles (id, organization_id, code, name, is_system_role) VALUES (?, ?, ?, ?, false)",
                id, org, code, code);
        for (String permission : permissions) {
            exec(c, "INSERT INTO vet.role_permissions (organization_id, role_id, permission_id) "
                    + "SELECT ?, ?, id FROM vet.permissions WHERE code = ?", org, id, permission);
        }
    }

    private static void user(Connection c, UUID id, UUID org, String email, UUID branch, UUID role) throws SQLException {
        exec(c, "INSERT INTO vet.app_users (id, organization_id, email, password_hash, display_name, is_active, mfa_enabled) "
                + "VALUES (?, ?, ?, vet.crypt(?, vet.gen_salt('bf', 4)), ?, true, false)", id, org, email, PASSWORD, email);
        exec(c, "INSERT INTO vet.user_clinic_roles (organization_id, user_id, clinic_id, role_id, valid_from) "
                + "VALUES (?, ?, ?, ?, CURRENT_DATE - 1)", org, id, branch, role);
    }

    private static void exec(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            ps.executeUpdate();
        }
    }
}
