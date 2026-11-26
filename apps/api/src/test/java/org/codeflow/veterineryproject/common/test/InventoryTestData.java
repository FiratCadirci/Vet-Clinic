package org.codeflow.veterineryproject.common.test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.UUID;

import static org.codeflow.veterineryproject.common.test.TenantTestData.*;

/**
 * Stok testleri için roller ve kullanıcılar (UUID öneki 0x24xxx). TenantTestData yapısını kullanır.
 * KEEPER_A1: A1-Şube1 + A1-Şube2 (aynı grup) + A2-Şube1 (başka grup), inventory.read/write.
 * KEEPER_A1S2: yalnız A1-Şube2. VET_A1: A1-Şube1, sistem veteriner hekim rolünün yetkileri (inventory.write yok).
 * KEEPER_B1: OrgB.
 */
public final class InventoryTestData {

    public static final UUID ROLE_KEEPER_A = uuid(0x24001);
    public static final UUID ROLE_VET_A = uuid(0x24002);
    public static final UUID ROLE_KEEPER_B = uuid(0x24003);

    public static final UUID KEEPER_A1 = uuid(0x24011);
    public static final UUID KEEPER_A1S2 = uuid(0x24012);
    public static final UUID VET_A1 = uuid(0x24013);
    public static final UUID KEEPER_B1 = uuid(0x24014);

    public static final String EMAIL_KEEPER_A1 = "inv-keeper-a1@test.com";
    public static final String EMAIL_KEEPER_A1S2 = "inv-keeper-a1s2@test.com";
    public static final String EMAIL_VET_A1 = "inv-vet-a1@test.com";
    public static final String EMAIL_KEEPER_B1 = "inv-keeper-b1@test.com";

    private static final UUID[] USERS = {KEEPER_A1, KEEPER_A1S2, VET_A1, KEEPER_B1};
    private static final UUID[] ROLES = {ROLE_KEEPER_A, ROLE_VET_A, ROLE_KEEPER_B};

    private static boolean seeded;
    private static Timestamp mark;

    private InventoryTestData() {
    }

    public static synchronized void seed(DataSource superUser) {
        TenantTestData.seed(superUser);
        if (seeded) {
            return;
        }
        try (Connection c = superUser.getConnection()) {
            try (var st = c.createStatement(); var rs = st.executeQuery("SELECT clock_timestamp()")) {
                rs.next();
                mark = rs.getTimestamp(1);
            }
            c.setAutoCommit(false);
            role(c, ROLE_KEEPER_A, ORG_A, "inv-keeper", "inventory.read", "inventory.write");
            // V22 sistem "veterinarian" rolünün yetkileri: stok yalnız okunur.
            role(c, ROLE_VET_A, ORG_A, "inv-vet", "clients.read", "clients.write", "appointments.manage",
                    "clinical.read", "clinical.write", "clinical.delete", "diagnostics.manage", "inventory.read",
                    "reports.read", "vaccine_protocols.manage");
            role(c, ROLE_KEEPER_B, ORG_B, "inv-keeper", "inventory.read", "inventory.write");
            user(c, KEEPER_A1, ORG_A, EMAIL_KEEPER_A1, BRANCH_A1_S1, ROLE_KEEPER_A);
            assign(c, ORG_A, KEEPER_A1, BRANCH_A1_S2, ROLE_KEEPER_A);
            assign(c, ORG_A, KEEPER_A1, BRANCH_A2_S1, ROLE_KEEPER_A);
            user(c, KEEPER_A1S2, ORG_A, EMAIL_KEEPER_A1S2, BRANCH_A1_S2, ROLE_KEEPER_A);
            user(c, VET_A1, ORG_A, EMAIL_VET_A1, BRANCH_A1_S1, ROLE_VET_A);
            user(c, KEEPER_B1, ORG_B, EMAIL_KEEPER_B1, BRANCH_B1_S1, ROLE_KEEPER_B);
            c.commit();
            seeded = true;
        } catch (SQLException ex) {
            throw new IllegalStateException("Stok test verisi yüklenemedi: " + ex.getMessage(), ex);
        }
    }

    /** Stok tabloları ve bu sınıfın kullanıcıları silinir (hareketler immutable olduğu için tetikleyiciler kapatılır). */
    public static synchronized void cleanup(DataSource superUser) {
        if (!seeded) {
            return;
        }
        try (Connection c = superUser.getConnection()) {
            c.setAutoCommit(false);
            exec(c, "SET LOCAL session_replication_role = replica");
            exec(c, "DELETE FROM vet.inventory_transaction_lines WHERE created_at >= ?", mark);
            exec(c, "DELETE FROM vet.inventory_transactions WHERE created_at >= ?", mark);
            exec(c, "DELETE FROM vet.inventory_lots WHERE created_at >= ?", mark);
            exec(c, "DELETE FROM vet.inventory_locations WHERE created_at >= ?", mark);
            exec(c, "DELETE FROM vet.products WHERE created_at >= ?", mark);
            exec(c, "DELETE FROM vet.sequence_counters WHERE counter_key = 'PRODUCT_SKU'");
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
            throw new IllegalStateException("Stok test verisi temizlenemedi: " + ex.getMessage(), ex);
        }
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
                + "VALUES (?, ?, ?, vet.crypt(?, vet.gen_salt('bf', 10)), ?, true, false)", id, org, email, PASSWORD, email);
        assign(c, org, id, branch, role);
    }

    private static void assign(Connection c, UUID org, UUID user, UUID branch, UUID role) throws SQLException {
        exec(c, "INSERT INTO vet.user_clinic_roles (organization_id, user_id, clinic_id, role_id, valid_from) "
                + "VALUES (?, ?, ?, ?, CURRENT_DATE - 1)", org, user, branch, role);
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
