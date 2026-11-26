package org.codeflow.veterineryproject.common.test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

import static org.codeflow.veterineryproject.common.test.TenantTestData.*;

/**
 * Kullanıcı / hayvan sahibi / hasta kaydı testleri için izinli roller ve kullanıcılar.
 * Ortak şifre: {@link TenantTestData#PASSWORD}. TenantTestData.seed(...) önce çağrılmış olmalıdır.
 *
 * OrgA/ClinicA1: MANAGER_A1 (A1-Sube1, users.manage + clients.*), READER_A1 (A1-Sube2, clients.read),
 * LIMITED_A1 (A1-Sube1, izinsiz). OrgA/ClinicA2: MANAGER_A2 (A2-Sube1, tek yönetici).
 * DUAL_A (A1-Sube1 yönetici + A2-Sube1 izinsiz rol). OrgB: MANAGER_B1.
 */
public final class RegistrationTestData {

    public static final UUID ROLE_MANAGER_A = uuid(0x81);
    public static final UUID ROLE_READER_A = uuid(0x82);
    public static final UUID ROLE_LIMITED_A = uuid(0x83);
    public static final UUID ROLE_MANAGER_B = uuid(0x84);
    public static final UUID ROLE_VET_A = uuid(0x85);
    public static final UUID ROLE_VET_NODELETE_A = uuid(0x86);
    public static final UUID ROLE_RECEPTION_A = uuid(0x87);
    public static final UUID ROLE_VET_B = uuid(0x88);
    public static final UUID ROLE_FRONTDESK_A = uuid(0x89);

    public static final UUID MANAGER_A1 = uuid(0x91);
    public static final UUID READER_A1 = uuid(0x92);
    public static final UUID LIMITED_A1 = uuid(0x93);
    public static final UUID MANAGER_A2 = uuid(0x94);
    public static final UUID DUAL_A = uuid(0x95);
    public static final UUID MANAGER_B1 = uuid(0x96);
    /** Klinik kayıt kullanıcıları (hekim profili olanlar: VET_A1, VET_A1_S2, VET_NODELETE_A1, VET_A2, VET_B1). */
    public static final UUID VET_A1 = uuid(0xA5);
    public static final UUID VET_A1_S2 = uuid(0xA6);
    public static final UUID VET_NODELETE_A1 = uuid(0xA7);
    public static final UUID RECEPTION_A1 = uuid(0xA8);
    public static final UUID VET_A2 = uuid(0xA9);
    public static final UUID VET_B1 = uuid(0xAA);
    public static final UUID VET_NOPROFILE_A1 = uuid(0xAB);
    public static final UUID FRONTDESK_A1 = uuid(0xAC);

    public static final String EMAIL_MANAGER_A1 = "mgr-a1@test.com";
    public static final String EMAIL_READER_A1 = "reader-a1@test.com";
    public static final String EMAIL_LIMITED_A1 = "limited-a1@test.com";
    public static final String EMAIL_MANAGER_A2 = "mgr-a2@test.com";
    public static final String EMAIL_DUAL_A = "dual-a@test.com";
    public static final String EMAIL_MANAGER_B1 = "mgr-b1@test.com";
    public static final String EMAIL_VET_A1 = "vet-a1@test.com";
    public static final String EMAIL_VET_A1_S2 = "vet-a1s2@test.com";
    public static final String EMAIL_VET_NODELETE_A1 = "vet-nodel-a1@test.com";
    public static final String EMAIL_RECEPTION_A1 = "reception-a1@test.com";
    public static final String EMAIL_VET_A2 = "vet-a2@test.com";
    public static final String EMAIL_VET_B1 = "vet-b1@test.com";
    public static final String EMAIL_VET_NOPROFILE_A1 = "vet-noprofile-a1@test.com";
    public static final String EMAIL_FRONTDESK_A1 = "frontdesk-a1@test.com";

    private static boolean seeded;
    private static java.sql.Timestamp mark;

    private RegistrationTestData() {
    }

    public static synchronized void seed(DataSource superUser) {
        TenantTestData.seed(superUser);
        if (seeded) {
            return;
        }
        try (Connection c = superUser.getConnection()) {
            mark = databaseNow(c);
            c.setAutoCommit(false);
            role(c, ROLE_MANAGER_A, ORG_A, "manager", "users.manage", "clients.read", "clients.write");
            role(c, ROLE_READER_A, ORG_A, "reader", "clients.read");
            role(c, ROLE_LIMITED_A, ORG_A, "limited");
            role(c, ROLE_MANAGER_B, ORG_B, "manager", "users.manage", "clients.read", "clients.write");
            user(c, MANAGER_A1, ORG_A, EMAIL_MANAGER_A1, BRANCH_A1_S1, ROLE_MANAGER_A);
            user(c, READER_A1, ORG_A, EMAIL_READER_A1, BRANCH_A1_S2, ROLE_READER_A);
            user(c, LIMITED_A1, ORG_A, EMAIL_LIMITED_A1, BRANCH_A1_S1, ROLE_LIMITED_A);
            user(c, MANAGER_A2, ORG_A, EMAIL_MANAGER_A2, BRANCH_A2_S1, ROLE_MANAGER_A);
            user(c, DUAL_A, ORG_A, EMAIL_DUAL_A, BRANCH_A1_S1, ROLE_MANAGER_A);
            assign(c, ORG_A, DUAL_A, BRANCH_A2_S1, ROLE_LIMITED_A);
            user(c, MANAGER_B1, ORG_B, EMAIL_MANAGER_B1, BRANCH_B1_S1, ROLE_MANAGER_B);
            role(c, ROLE_VET_A, ORG_A, "vet", "clinical.read", "clinical.write", "clinical.delete", "vaccine_protocols.manage", "appointments.manage");
            role(c, ROLE_VET_NODELETE_A, ORG_A, "vet-nodelete", "clinical.read", "clinical.write");
            role(c, ROLE_RECEPTION_A, ORG_A, "reception", "clinical.read", "appointments.manage");
            role(c, ROLE_VET_B, ORG_B, "vet", "clinical.read", "clinical.write", "clinical.delete", "vaccine_protocols.manage");
            user(c, VET_A1, ORG_A, EMAIL_VET_A1, BRANCH_A1_S1, ROLE_VET_A);
            user(c, VET_A1_S2, ORG_A, EMAIL_VET_A1_S2, BRANCH_A1_S2, ROLE_VET_A);
            user(c, VET_NODELETE_A1, ORG_A, EMAIL_VET_NODELETE_A1, BRANCH_A1_S1, ROLE_VET_NODELETE_A);
            user(c, RECEPTION_A1, ORG_A, EMAIL_RECEPTION_A1, BRANCH_A1_S1, ROLE_RECEPTION_A);
            user(c, VET_A2, ORG_A, EMAIL_VET_A2, BRANCH_A2_S1, ROLE_VET_A);
            user(c, VET_B1, ORG_B, EMAIL_VET_B1, BRANCH_B1_S1, ROLE_VET_B);
            user(c, VET_NOPROFILE_A1, ORG_A, EMAIL_VET_NOPROFILE_A1, BRANCH_A1_S1, ROLE_VET_A);
            role(c, ROLE_FRONTDESK_A, ORG_A, "frontdesk", "clients.read", "clients.write", "appointments.manage", "billing.manage");
            user(c, FRONTDESK_A1, ORG_A, EMAIL_FRONTDESK_A1, BRANCH_A1_S1, ROLE_FRONTDESK_A);
            veterinarian(c, ORG_A, VET_A1, "LIC-A1");
            veterinarian(c, ORG_A, VET_A1_S2, "LIC-A1S2");
            veterinarian(c, ORG_A, VET_NODELETE_A1, "LIC-NODEL");
            veterinarian(c, ORG_A, VET_A2, "LIC-A2");
            veterinarian(c, ORG_B, VET_B1, "LIC-B1");
            c.commit();
            seeded = true;
        } catch (SQLException ex) {
            throw new IllegalStateException("Kayıt test verisi yüklenemedi: " + ex.getMessage(), ex);
        }
    }

    /** Seed ve testlerin oluşturduğu satırları siler; diğer IT sınıfları satır sayısı varsayar. */
    public static synchronized void cleanup(DataSource superUser) {
        if (!seeded) {
            return;
        }
        try (Connection c = superUser.getConnection()) {
            c.setAutoCommit(false);
            exec(c, "SET LOCAL session_replication_role = replica");
            exec(c, "DELETE FROM vet.appointment_status_history WHERE appointment_id IN (SELECT id FROM vet.appointments WHERE created_at >= ?)", mark);
            exec(c, "DELETE FROM vet.audit_logs WHERE occurred_at >= ?", mark);
            exec(c, "DELETE FROM vet.refresh_tokens WHERE user_id IN (SELECT id FROM vet.app_users WHERE created_at >= ?)", mark);
            exec(c, "DELETE FROM vet.invoice_payments WHERE applied_at >= ?", mark);
            exec(c, "DELETE FROM vet.invoice_lines WHERE invoice_id IN (SELECT id FROM vet.invoices WHERE created_at >= ?)", mark);
            exec(c, "DELETE FROM vet.client_clinic_access WHERE granted_at >= ?", mark);
            for (String table : new String[]{"payments", "invoices", "animal_weights", "animal_allergies", "animal_identifiers", "clinical_documents", "encounter_addenda", "encounter_treatments", "vaccine_records", "encounters", "appointments",
                    "vaccine_protocol_doses", "vaccine_protocols", "veterinarians", "staff_members", "animal_owners", "animals", "client_contacts", "client_addresses", "clients",
                    "user_clinic_roles", "role_permissions", "roles", "app_users"}) {
                exec(c, "DELETE FROM vet." + table + " WHERE created_at >= ?", mark);
            }
            exec(c, "DELETE FROM vet.breeds WHERE name LIKE ?", "%-IT");
            exec(c, "DELETE FROM vet.patient_number_counters");
            exec(c, "DELETE FROM vet.sequence_counters");
            c.commit();
            seeded = false;
        } catch (SQLException ex) {
            throw new IllegalStateException("Kayıt test verisi temizlenemedi: " + ex.getMessage(), ex);
        }
    }

    private static java.sql.Timestamp databaseNow(Connection c) throws SQLException {
        try (java.sql.Statement st = c.createStatement();
             java.sql.ResultSet rs = st.executeQuery("SELECT clock_timestamp()")) {
            rs.next();
            return rs.getTimestamp(1);
        }
    }

    private static void role(Connection c, UUID id, UUID org, String code, String... permissions) throws SQLException {
        exec(c, "INSERT INTO vet.roles (id, organization_id, code, name, is_system_role) VALUES (?, ?, ?, ?, false)", id, org, code, code);
        for (String permission : permissions) {
            exec(c, "INSERT INTO vet.role_permissions (organization_id, role_id, permission_id) SELECT ?, ?, id FROM vet.permissions WHERE code = ?",
                    org, id, permission);
        }
    }

    private static void user(Connection c, UUID id, UUID org, String email, UUID branch, UUID role) throws SQLException {
        exec(c, "INSERT INTO vet.app_users (id, organization_id, email, password_hash, display_name, is_active, mfa_enabled) VALUES (?, ?, ?, vet.crypt(?, vet.gen_salt('bf', 10)), ?, true, false)",
                id, org, email, PASSWORD, email);
        assign(c, org, id, branch, role);
    }

    private static void veterinarian(Connection c, UUID org, UUID user, String license) throws SQLException {
        UUID staff = UUID.randomUUID();
        exec(c, "INSERT INTO vet.staff_members (id, organization_id, user_id, full_name, is_active) VALUES (?, ?, ?, ?, true)", staff, org, user, license);
        exec(c, "INSERT INTO vet.veterinarians (organization_id, staff_member_id, license_no, is_active) VALUES (?, ?, ?, true)", org, staff, license);
    }
    private static void assign(Connection c, UUID org, UUID user, UUID branch, UUID role) throws SQLException {
        exec(c, "INSERT INTO vet.user_clinic_roles (organization_id, user_id, clinic_id, role_id, valid_from) VALUES (?, ?, ?, ?, CURRENT_DATE - 1)",
                org, user, branch, role);
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
