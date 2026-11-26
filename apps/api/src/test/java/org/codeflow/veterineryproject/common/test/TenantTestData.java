package org.codeflow.veterineryproject.common.test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

/**
 * Hiyerarşi: Organizasyon -> Klinik (clinic_groups) -> Şube (clinics).
 * OrgA: ClinicA1 {A1-Sube1, A1-Sube2}, ClinicA2 {A2-Sube1}. OrgB: ClinicB1 {B1-Sube1}.
 * Her şubede kayıtlı birer hasta (müşteri + hayvan) ve randevu vardır.
 */
public final class TenantTestData {

    public static final String PASSWORD = "secret";

    public static final UUID ORG_A = uuid(0xA0);
    public static final UUID ORG_B = uuid(0xB0);

    public static final UUID CLINIC_A1 = uuid(0xA1);
    public static final UUID CLINIC_A2 = uuid(0xA2);
    public static final UUID CLINIC_B1 = uuid(0xB1);

    public static final UUID BRANCH_A1_S1 = uuid(0x11);
    public static final UUID BRANCH_A1_S2 = uuid(0x12);
    public static final UUID BRANCH_A2_S1 = uuid(0x21);
    public static final UUID BRANCH_B1_S1 = uuid(0x31);

    public static final UUID USER_A1_S1 = uuid(0x41);
    public static final UUID USER_A1_S2 = uuid(0x42);
    public static final UUID USER_A2_S1 = uuid(0x43);
    public static final UUID USER_B1_S1 = uuid(0x44);

    public static final String EMAIL_A1_S1 = "a1s1@test.com";
    public static final String EMAIL_A1_S2 = "a1s2@test.com";
    public static final String EMAIL_A2_S1 = "a2s1@test.com";
    public static final String EMAIL_B1_S1 = "b1s1@test.com";
    public static final int ORG_A_CODE = 9001;
    public static final int ORG_B_CODE = 9002;

    public static final UUID PLATFORM_ADMIN = uuid(0x99);
    public static final String PLATFORM_EMAIL = "platform@test.com";

    public static final UUID PATIENT_A1_S1 = uuid(0x51);
    public static final UUID PATIENT_A1_S2 = uuid(0x52);
    public static final UUID PATIENT_A2_S1 = uuid(0x53);
    public static final UUID PATIENT_B1_S1 = uuid(0x54);

    public static final UUID APPT_A1_S1 = uuid(0x61);
    public static final UUID APPT_A1_S2 = uuid(0x62);
    public static final UUID APPT_A2_S1 = uuid(0x63);
    public static final UUID APPT_B1_S1 = uuid(0x64);

    private static boolean seeded;

    private TenantTestData() {
    }

    public static UUID uuid(int n) {
        return UUID.fromString(String.format("00000000-0000-4000-8000-%012x", n));
    }

    public static synchronized void seed(DataSource superUser) {
        if (seeded) {
            return;
        }
        try (Connection c = superUser.getConnection()) {
            c.setAutoCommit(false);
            seedStructure(c);
            seedPatient(c, ORG_A, USER_A1_S1, BRANCH_A1_S1, PATIENT_A1_S1, APPT_A1_S1, "A1S1");
            seedPatient(c, ORG_A, USER_A1_S2, BRANCH_A1_S2, PATIENT_A1_S2, APPT_A1_S2, "A1S2");
            seedPatient(c, ORG_A, USER_A2_S1, BRANCH_A2_S1, PATIENT_A2_S1, APPT_A2_S1, "A2S1");
            seedPatient(c, ORG_B, USER_B1_S1, BRANCH_B1_S1, PATIENT_B1_S1, APPT_B1_S1, "B1S1");
            c.commit();
            seeded = true;
        } catch (SQLException ex) {
            throw new IllegalStateException("Test verisi yüklenemedi: " + ex.getMessage(), ex);
        }
    }

    private static void seedStructure(Connection c) throws SQLException {
        org(c, ORG_A, ORG_A_CODE);
        org(c, ORG_B, ORG_B_CODE);
        group(c, CLINIC_A1, ORG_A, "CA1");
        group(c, CLINIC_A2, ORG_A, "CA2");
        group(c, CLINIC_B1, ORG_B, "CB1");
        branch(c, BRANCH_A1_S1, ORG_A, CLINIC_A1, "A1S1");
        branch(c, BRANCH_A1_S2, ORG_A, CLINIC_A1, "A1S2");
        branch(c, BRANCH_A2_S1, ORG_A, CLINIC_A2, "A2S1");
        branch(c, BRANCH_B1_S1, ORG_B, CLINIC_B1, "B1S1");
        UUID roleA = uuid(0x71);
        UUID roleB = uuid(0x72);
        role(c, roleA, ORG_A);
        role(c, roleB, ORG_B);
        user(c, USER_A1_S1, ORG_A, EMAIL_A1_S1, BRANCH_A1_S1, roleA);
        user(c, USER_A1_S2, ORG_A, EMAIL_A1_S2, BRANCH_A1_S2, roleA);
        user(c, USER_A2_S1, ORG_A, EMAIL_A2_S1, BRANCH_A2_S1, roleA);
        user(c, USER_B1_S1, ORG_B, EMAIL_B1_S1, BRANCH_B1_S1, roleB);
        exec(c, "INSERT INTO vet.platform_admins (id, email, password_hash, display_name, is_active) VALUES (?, ?, vet.crypt(?, vet.gen_salt('bf', 10)), 'Platform', true)",
                PLATFORM_ADMIN, PLATFORM_EMAIL, PASSWORD);
    }

    private static void seedPatient(Connection c, UUID org, UUID user, UUID branch, UUID patient, UUID appointment, String tag) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute("SELECT set_config('app.organization_id', '" + org + "', true), set_config('app.user_id', '" + user
                    + "', true), set_config('app.clinic_id', '" + branch + "', true)");
        }
        exec(c, "INSERT INTO vet.clients (id, organization_id, display_name) VALUES (?, ?, ?)", patient, org, "Client " + tag);
        exec(c, "INSERT INTO vet.animals (id, organization_id, patient_no, name, species_id) SELECT ?, ?, ?, ?, id FROM vet.species ORDER BY code LIMIT 1",
                patient, org, "PN-" + tag, "Animal " + tag);
        exec(c, "INSERT INTO vet.appointments (id, organization_id, clinic_id, client_id, animal_id, appointment_no, starts_at, ends_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, now() + interval '1 day', now() + interval '1 day 1 hour')",
                appointment, org, branch, patient, patient, "APT-" + tag);
    }

    private static void org(Connection c, UUID id, int code) throws SQLException {
        String name = "ORG" + code;
        exec(c, "INSERT INTO vet.organizations (id, code, name, legal_name, default_currency, timezone_name, is_active) VALUES (?, ?, ?, ?, 'TRY', 'Europe/Istanbul', true)",
                id, code, name, name);
    }

    private static void group(Connection c, UUID id, UUID org, String code) throws SQLException {
        exec(c, "INSERT INTO vet.clinic_groups (id, organization_id, code, name) VALUES (?, ?, ?, ?)", id, org, code, code);
    }

    private static void branch(Connection c, UUID id, UUID org, UUID group, String code) throws SQLException {
        exec(c, "INSERT INTO vet.clinics (id, organization_id, clinic_group_id, code, name) VALUES (?, ?, ?, ?, ?)", id, org, group, code, code);
    }

    private static void role(Connection c, UUID id, UUID org) throws SQLException {
        exec(c, "INSERT INTO vet.roles (id, organization_id, code, name, is_system_role) VALUES (?, ?, 'admin', 'Admin', true)", id, org);
        // Randevu uçları (liste/detay dahil) appointments.manage ister; izolasyon testleri yetkili kullanıcıyla yapılır.
        exec(c, "INSERT INTO vet.role_permissions (organization_id, role_id, permission_id) SELECT ?, ?, id FROM vet.permissions WHERE code = 'appointments.manage'", org, id);
    }

    private static void user(Connection c, UUID id, UUID org, String email, UUID branch, UUID role) throws SQLException {
        exec(c, "INSERT INTO vet.app_users (id, organization_id, email, password_hash, display_name, is_active, mfa_enabled) VALUES (?, ?, ?, vet.crypt(?, vet.gen_salt('bf', 10)), ?, true, false)",
                id, org, email, PASSWORD, email);
        exec(c, "INSERT INTO vet.user_clinic_roles (organization_id, user_id, clinic_id, role_id, valid_from) VALUES (?, ?, ?, ?, CURRENT_DATE - 1)",
                org, id, branch, role);
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
