package org.codeflow.veterineryproject.notification;

import org.codeflow.veterineryproject.common.test.RegistrationTestData;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.codeflow.veterineryproject.common.test.TenantTestData.*;

/**
 * Bildirim testleri verisi (UUID öneki 0x2500xx). RegistrationTestData.cleanup bu satırları da sildiği için
 * her test sınıfında yeniden tohumlanır; bildirim tabloları {@link #cleanup(DataSource)} ile boşaltılır.
 *
 * OrgA / A1-Şube1: ADMIN_A1 (audit.read + users.manage + randevu), STOCK_A1 (inventory.write),
 * RECEPTION_NOTIF_A1 (yalnız randevu). OrgA / A1-Şube2: STOCK_A1_S2 (inventory.write, başka şube).
 * OrgB: ADMIN_B1 (audit.read + users.manage).
 * Hayvan sahibi: OWNER_A (mobil hesap OWNER_USER_A + e-posta), hayvanı PET_A (A1-Şube1);
 * OWNER_A2 yalnız e-postalı (mobil hesabı yok), hayvanı PET_A2.
 */
public final class NotificationTestData {

    public static final UUID ROLE_ADMIN_A = uuid(0x250001);
    public static final UUID ROLE_STOCK_A = uuid(0x250002);
    public static final UUID ROLE_RECEPTION_A = uuid(0x250003);
    public static final UUID ROLE_ADMIN_B = uuid(0x250004);

    public static final UUID ADMIN_A1 = uuid(0x250011);
    public static final UUID STOCK_A1 = uuid(0x250012);
    public static final UUID STOCK_A1_S2 = uuid(0x250013);
    public static final UUID RECEPTION_NOTIF_A1 = uuid(0x250014);
    public static final UUID ADMIN_B1 = uuid(0x250015);
    public static final UUID OWNER_USER_A = uuid(0x250016);

    public static final UUID OWNER_A = uuid(0x250021);
    public static final UUID OWNER_A2 = uuid(0x250022);
    public static final UUID PET_A = uuid(0x250031);
    public static final UUID PET_A2 = uuid(0x250032);

    public static final String EMAIL_ADMIN_A1 = "notif-admin-a1@test.com";
    public static final String EMAIL_STOCK_A1 = "notif-stock-a1@test.com";
    public static final String EMAIL_STOCK_A1_S2 = "notif-stock-a1s2@test.com";
    public static final String EMAIL_RECEPTION_NOTIF_A1 = "notif-reception-a1@test.com";
    public static final String EMAIL_ADMIN_B1 = "notif-admin-b1@test.com";
    public static final String EMAIL_OWNER_USER_A = "notif-owner-login@test.com";
    public static final String EMAIL_OWNER_A = "notif-owner-a@test.com";
    public static final String EMAIL_OWNER_A2 = "notif-owner-a2@test.com";

    public static final String PET_A_NAME = "Pamuk";

    private static final AtomicInteger SLOT = new AtomicInteger(400);
    private static boolean seeded;

    private NotificationTestData() {
    }

    public static synchronized void seed(DataSource superUser) {
        RegistrationTestData.seed(superUser);
        if (seeded) {
            return;
        }
        try (Connection c = superUser.getConnection()) {
            c.setAutoCommit(false);
            role(c, ROLE_ADMIN_A, ORG_A, "notif-admin", "audit.read", "users.manage", "appointments.manage", "clients.read");
            role(c, ROLE_STOCK_A, ORG_A, "notif-stock", "inventory.read", "inventory.write");
            role(c, ROLE_RECEPTION_A, ORG_A, "notif-reception", "appointments.manage");
            role(c, ROLE_ADMIN_B, ORG_B, "notif-admin", "audit.read", "users.manage");
            user(c, ADMIN_A1, ORG_A, EMAIL_ADMIN_A1, "Yönetici A1", BRANCH_A1_S1, ROLE_ADMIN_A);
            user(c, STOCK_A1, ORG_A, EMAIL_STOCK_A1, "Stok A1", BRANCH_A1_S1, ROLE_STOCK_A);
            user(c, STOCK_A1_S2, ORG_A, EMAIL_STOCK_A1_S2, "Stok A1 Şube2", BRANCH_A1_S2, ROLE_STOCK_A);
            user(c, RECEPTION_NOTIF_A1, ORG_A, EMAIL_RECEPTION_NOTIF_A1, "Resepsiyon A1", BRANCH_A1_S1, ROLE_RECEPTION_A);
            user(c, ADMIN_B1, ORG_B, EMAIL_ADMIN_B1, "Yönetici B1", BRANCH_B1_S1, ROLE_ADMIN_B);
            exec(c, "INSERT INTO vet.app_users (id, organization_id, email, password_hash, display_name, is_active, mfa_enabled) "
                    + "VALUES (?, ?, ?, vet.crypt(?, vet.gen_salt('bf', 4)), 'Sahip A', true, false)", OWNER_USER_A, ORG_A, EMAIL_OWNER_USER_A, PASSWORD);

            // Müşteri/hayvan kayıtları şube erişimini tetikleyiciyle alır; bağlam A1-Şube1 kullanıcısıdır.
            try (Statement st = c.createStatement()) {
                st.execute("SELECT set_config('app.organization_id', '" + ORG_A + "', true), set_config('app.user_id', '"
                        + USER_A1_S1 + "', true), set_config('app.clinic_id', '" + BRANCH_A1_S1 + "', true)");
            }
            owner(c, OWNER_A, OWNER_USER_A, "Ayşe Sahip", EMAIL_OWNER_A, PET_A, PET_A_NAME);
            owner(c, OWNER_A2, null, "Mehmet Sahip", EMAIL_OWNER_A2, PET_A2, "Boncuk");
            c.commit();
            seeded = true;
        } catch (SQLException ex) {
            throw new IllegalStateException("Bildirim test verisi yüklenemedi: " + ex.getMessage(), ex);
        }
    }

    /** Bildirim tablolarını boşaltır (RegistrationTestData.cleanup kullanıcı/müşteri satırlarını zaten siler). */
    public static synchronized void cleanup(DataSource superUser) {
        try (Connection c = superUser.getConnection(); Statement st = c.createStatement()) {
            st.execute("DELETE FROM vet.vaccine_reminder_dispatches");
            st.execute("DELETE FROM vet.push_devices");
            st.execute("DELETE FROM vet.notification_preferences");
            st.execute("DELETE FROM vet.notification_outbox");
        } catch (SQLException ex) {
            throw new IllegalStateException("Bildirim test verisi temizlenemedi: " + ex.getMessage(), ex);
        }
        seeded = false;
    }

    /** PET_A için A1-Şube1'de BOOKED randevu (her çağrıda farklı gün, hekimsiz). */
    public static UUID appointment(DataSource superUser) {
        UUID id = UUID.randomUUID();
        int day = SLOT.incrementAndGet();
        try (Connection c = superUser.getConnection()) {
            exec(c, "INSERT INTO vet.appointments (id, organization_id, clinic_id, client_id, animal_id, appointment_no, status, starts_at, ends_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, 'BOOKED', date_trunc('hour', now()) + make_interval(days => ?), date_trunc('hour', now()) + make_interval(days => ?, hours => 1))",
                    id, ORG_A, BRANCH_A1_S1, OWNER_A, PET_A, "NT-" + id.toString().substring(0, 12), day, day);
        } catch (SQLException ex) {
            throw new IllegalStateException(ex.getMessage(), ex);
        }
        return id;
    }

    public static void addPushDevice(DataSource superUser, UUID userId, String token) {
        try (Connection c = superUser.getConnection()) {
            exec(c, "INSERT INTO vet.push_devices (organization_id, user_id, expo_push_token, platform) VALUES (?, ?, ?, 'ANDROID') "
                    + "ON CONFLICT (organization_id, expo_push_token) DO UPDATE SET user_id = excluded.user_id", ORG_A, userId, token);
        } catch (SQLException ex) {
            throw new IllegalStateException(ex.getMessage(), ex);
        }
    }

    private static void owner(Connection c, UUID clientId, UUID appUser, String name, String email, UUID petId, String petName)
            throws SQLException {
        exec(c, "INSERT INTO vet.clients (id, organization_id, display_name, app_user_id) VALUES (?, ?, ?, ?)", clientId, ORG_A, name, appUser);
        exec(c, "INSERT INTO vet.client_contacts (organization_id, client_id, contact_type, contact_value, is_primary) VALUES (?, ?, 'EMAIL', ?, true)",
                ORG_A, clientId, email);
        exec(c, "INSERT INTO vet.animals (id, organization_id, patient_no, name, species_id) SELECT ?, ?, ?, ?, id FROM vet.species WHERE code = 'DOG'",
                petId, ORG_A, "NT-" + petId.toString().substring(30), petName);
        exec(c, "INSERT INTO vet.animal_owners (organization_id, animal_id, client_id, relationship_type, is_primary_contact) VALUES (?, ?, ?, 'OWNER', true)",
                ORG_A, petId, clientId);
    }

    private static void role(Connection c, UUID id, UUID org, String code, String... permissions) throws SQLException {
        exec(c, "INSERT INTO vet.roles (id, organization_id, code, name, is_system_role) VALUES (?, ?, ?, ?, false)", id, org, code, code);
        for (String permission : permissions) {
            exec(c, "INSERT INTO vet.role_permissions (organization_id, role_id, permission_id) SELECT ?, ?, id FROM vet.permissions WHERE code = ?",
                    org, id, permission);
        }
    }

    private static void user(Connection c, UUID id, UUID org, String email, String name, UUID branch, UUID role) throws SQLException {
        exec(c, "INSERT INTO vet.app_users (id, organization_id, email, password_hash, display_name, is_active, mfa_enabled) "
                + "VALUES (?, ?, ?, vet.crypt(?, vet.gen_salt('bf', 4)), ?, true, false)", id, org, email, PASSWORD, name);
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
