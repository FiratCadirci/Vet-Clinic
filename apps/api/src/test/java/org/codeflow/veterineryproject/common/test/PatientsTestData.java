package org.codeflow.veterineryproject.common.test;

import javax.sql.DataSource;
import java.util.UUID;

import static org.codeflow.veterineryproject.common.test.RegistrationTestData.ROLE_VET_A;
import static org.codeflow.veterineryproject.common.test.TenantTestData.*;

/**
 * Hasta / randevu (V26) testlerinin kendi verisi; sabit UUID'ler 0x26xxxx önekini kullanır.
 * Kendi hekimi (kullanıcısız personel kaydı) ve hizmetleri vardır: diğer testlerin hekimlerine çalışma saati eklenmez,
 * böylece çalışma saati trigger'ı başka IT sınıflarının randevularını etkilemez.
 * RegistrationTestData.seed(...) önce çağrılmış olmalıdır.
 */
public final class PatientsTestData {

    /** Uygun saat testlerinin hekimi: A1-Şube1'de her gün 09:00–12:00 ve 13:00–17:00 çalışır. */
    public static final UUID SLOT_VET = uuid(0x260001);
    private static final UUID SLOT_VET_STAFF = uuid(0x260002);
    public static final UUID SERVICE_30 = uuid(0x260011);
    public static final UUID SERVICE_45 = uuid(0x260012);
    public static final UUID SERVICE_NO_DURATION = uuid(0x260013);
    public static final UUID SERVICE_ORG_B = uuid(0x260014);

    /** "Tüm şubeler" yetkili hekim: A1-Şube1 ve A2-Şube1'de (iki ayrı klinik grubu) hekim rolü. */
    public static final UUID ALL_BRANCH_VET = uuid(0x260021);
    public static final String EMAIL_ALL_BRANCH_VET = "pt-allbranch-vet@test.com";

    private static boolean seeded;

    private PatientsTestData() {
    }

    public static synchronized void seed(DataSource superUser) {
        RegistrationTestData.seed(superUser);
        if (seeded) {
            return;
        }
        ClinicalTestData.update(superUser, "INSERT INTO vet.staff_members (id, organization_id, full_name, is_active) VALUES (?, ?, 'PT Slot Hekim', true)",
                SLOT_VET_STAFF, ORG_A);
        ClinicalTestData.update(superUser, "INSERT INTO vet.veterinarians (id, organization_id, staff_member_id, license_no, is_active) VALUES (?, ?, ?, 'LIC-PT-SLOT', true)",
                SLOT_VET, ORG_A, SLOT_VET_STAFF);
        for (int day = 0; day < 7; day++) {
            workingHours(superUser, day, "09:00", "12:00");
            workingHours(superUser, day, "13:00", "17:00");
        }
        service(superUser, SERVICE_30, ORG_A, "PT-MUAYENE", "PT Muayene", 30);
        service(superUser, SERVICE_45, ORG_A, "PT-ASI", "PT Aşı", 45);
        service(superUser, SERVICE_NO_DURATION, ORG_A, "PT-SURESIZ", "PT Süresiz", null);
        service(superUser, SERVICE_ORG_B, ORG_B, "PT-B", "PT B Hizmet", 30);

        ClinicalTestData.update(superUser, "INSERT INTO vet.app_users (id, organization_id, email, password_hash, display_name, is_active, mfa_enabled) "
                + "VALUES (?, ?, ?, vet.crypt(?, vet.gen_salt('bf', 10)), 'PT Tüm Şube Hekim', true, false)",
                ALL_BRANCH_VET, ORG_A, EMAIL_ALL_BRANCH_VET, PASSWORD);
        for (UUID branch : new UUID[]{BRANCH_A1_S1, BRANCH_A2_S1}) {
            ClinicalTestData.update(superUser, "INSERT INTO vet.user_clinic_roles (organization_id, user_id, clinic_id, role_id, valid_from) VALUES (?, ?, ?, ?, CURRENT_DATE - 1)",
                    ORG_A, ALL_BRANCH_VET, branch, ROLE_VET_A);
        }
        seeded = true;
    }

    /**
     * Ortak temizliğin (RegistrationTestData.cleanup) silmediği satırlar: hizmetler ve çalışma saatleri.
     * Kullanıcı / rol satırları ortak temizlikte silinir (oluşturulma zamanına göre).
     */
    public static synchronized void cleanup(DataSource superUser) {
        if (!seeded) {
            return;
        }
        ClinicalTestData.update(superUser, "DELETE FROM vet.appointment_status_history WHERE appointment_id IN (SELECT id FROM vet.appointments WHERE veterinarian_id = ?)", SLOT_VET);
        ClinicalTestData.update(superUser, "DELETE FROM vet.appointments WHERE veterinarian_id = ?", SLOT_VET);
        ClinicalTestData.update(superUser, "DELETE FROM vet.veterinarian_working_hours WHERE veterinarian_id = ?", SLOT_VET);
        ClinicalTestData.update(superUser, "DELETE FROM vet.veterinarians WHERE id = ?", SLOT_VET);
        ClinicalTestData.update(superUser, "DELETE FROM vet.staff_members WHERE id = ?", SLOT_VET_STAFF);
        ClinicalTestData.update(superUser, "DELETE FROM vet.service_catalog WHERE code LIKE 'PT-%'");
        seeded = false;
    }

    private static void workingHours(DataSource superUser, int day, String from, String to) {
        ClinicalTestData.update(superUser, """
                INSERT INTO vet.veterinarian_working_hours
                    (organization_id, clinic_id, veterinarian_id, day_of_week, starts_at, ends_at, effective_from)
                VALUES (?, ?, ?, ?, ?::time, ?::time, CURRENT_DATE - 30)
                """, ORG_A, BRANCH_A1_S1, SLOT_VET, day, from, to);
    }

    private static void service(DataSource superUser, UUID id, UUID org, String code, String name, Integer minutes) {
        ClinicalTestData.update(superUser, "INSERT INTO vet.service_catalog (id, organization_id, code, name, duration_minutes, default_price) VALUES (?, ?, ?, ?, ?, 250)",
                id, org, code, name, minutes);
    }
}
