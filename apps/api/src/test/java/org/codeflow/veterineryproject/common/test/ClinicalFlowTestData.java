package org.codeflow.veterineryproject.common.test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.UUID;

import static org.codeflow.veterineryproject.common.test.TenantTestData.ORG_A;
import static org.codeflow.veterineryproject.common.test.TenantTestData.uuid;

/**
 * Muayene türleri / reçete / aşı şablonu testleri için OrgA hastaları (UUID öneki 0x27xxx). Kullanıcılar
 * RegistrationTestData'dan (VET_A1, RECEPTION_A1, VET_A1_S2, VET_B1), stok için InventoryTestData.KEEPER_A1.
 * Hasta ve müşteri satırları RegistrationTestData.cleanup ile silinir; bu sınıf reçete, ölçüm ve stok satırlarını siler.
 */
public final class ClinicalFlowTestData {

    public static final UUID OWNER = uuid(0x27001);
    public static final UUID DOG = uuid(0x27011);
    public static final UUID PUPPY = uuid(0x27012);
    public static final UUID CAT = uuid(0x27013);
    public static final UUID RABBIT = uuid(0x27014);
    public static final UUID BIRD = uuid(0x27015);
    public static final UUID DECEASED_DOG = uuid(0x27016);
    public static final String OWNER_NAME = "Akış Sahibi";
    public static final String OWNER_PHONE = "+905550002701";

    private static boolean seeded;
    private static Timestamp mark;

    private ClinicalFlowTestData() {
    }

    public static synchronized void seed(DataSource superUser) {
        InventoryTestData.seed(superUser);
        RegistrationTestData.seed(superUser);
        if (seeded) {
            return;
        }
        try (Connection c = superUser.getConnection()) {
            try (var st = c.createStatement(); var rs = st.executeQuery("SELECT clock_timestamp()")) {
                rs.next();
                mark = rs.getTimestamp(1);
            }
            c.setAutoCommit(false);
            try (var st = c.createStatement()) {
                // Hasta kaydı tetikleyicisi kaydı açan şubeyi bağlamdan alır.
                st.execute("SELECT set_config('app.organization_id', '" + ORG_A + "', true), set_config('app.user_id', '"
                        + TenantTestData.USER_A1_S1 + "', true), set_config('app.clinic_id', '" + TenantTestData.BRANCH_A1_S1 + "', true)");
            }
            exec(c, "INSERT INTO vet.clients (id, organization_id, display_name) VALUES (?, ?, ?)", OWNER, ORG_A, OWNER_NAME);
            exec(c, "INSERT INTO vet.client_contacts (organization_id, client_id, contact_type, contact_value, is_primary) "
                    + "VALUES (?, ?, 'PHONE', ?, true)", ORG_A, OWNER, OWNER_PHONE);
            animal(c, DOG, "DOG", 1000, false, "Karabaş");
            animal(c, PUPPY, "DOG", 60, false, "Yavru");
            animal(c, CAT, "CAT", 400, false, "Tekir");
            animal(c, RABBIT, "RABBIT", 200, false, "Pamuk");
            animal(c, BIRD, "BIRD", 300, false, "Maviş");
            animal(c, DECEASED_DOG, "DOG", 3000, true, "Eski");
            c.commit();
            seeded = true;
        } catch (SQLException ex) {
            throw new IllegalStateException("Klinik akış test verisi yüklenemedi: " + ex.getMessage(), ex);
        }
    }

    public static synchronized void cleanup(DataSource superUser) {
        if (seeded) {
            try (Connection c = superUser.getConnection()) {
                c.setAutoCommit(false);
                exec(c, "SET LOCAL session_replication_role = replica");
                exec(c, "DELETE FROM vet.prescription_items WHERE prescription_id IN (SELECT id FROM vet.prescriptions WHERE created_at >= ?)", mark);
                exec(c, "DELETE FROM vet.prescriptions WHERE created_at >= ?", mark);
                exec(c, "DELETE FROM vet.encounter_vitals WHERE measured_at >= ?", mark);
                exec(c, "DELETE FROM vet.client_contacts WHERE client_id = ?", OWNER);
                c.commit();
                seeded = false;
            } catch (SQLException ex) {
                throw new IllegalStateException("Klinik akış test verisi temizlenemedi: " + ex.getMessage(), ex);
            }
        }
        InventoryTestData.cleanup(superUser);
    }

    private static void animal(Connection c, UUID id, String species, int ageDays, boolean deceased, String name) throws SQLException {
        exec(c, "INSERT INTO vet.animals (id, organization_id, patient_no, name, species_id, birth_date, deceased_at) "
                        + "SELECT ?, ?, ?, ?, id, current_date - ?, CASE WHEN ? THEN now() END FROM vet.species WHERE code = ?",
                id, ORG_A, "PN-27-" + Integer.toHexString(id.hashCode()), name, ageDays, deceased, species);
        exec(c, "INSERT INTO vet.animal_owners (organization_id, animal_id, client_id, is_primary_contact) VALUES (?, ?, ?, true)",
                ORG_A, id, OWNER);
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
