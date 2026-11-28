package org.codeflow.veterineryproject.common.test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.codeflow.veterineryproject.common.test.TenantTestData.PASSWORD;
import static org.codeflow.veterineryproject.common.test.TenantTestData.uuid;

/**
 * Rapor, genel bakış ve denetim izi testleri için bilinen veri (UUID öneki 0x31xxxx, organizasyon kodları 9731/9732).
 * Diğer testlerin verisiyle karışmasın diye kendi organizasyonlarını kurar; kesin sayılar bu sınıftaki yoruma göredir.
 * <p>
 * OrgR: grup RG1 {R1 "R-Merkez", R2 "R-Kadıköy"}, grup RG2 {R3 "R-Ankara"}. OrgQ: {Q1} (izolasyon).
 * Kullanıcılar: ADMIN (R1+R2+R3, yönetici yetkileri), MANAGER_R2 (yalnız R2, aynı yetkiler),
 * RECEPTION (R1, V22 resepsiyon yetkileri: rapor/denetim yok), ADMIN_Q (Q1).
 * <p>
 * Mart 2026 (1 Mart Pazar; Pazartesi ve Salı 5'er, Çarşamba ve Perşembe 4'er kez):
 * <ul>
 *   <li>Çalışma saatleri: VET1@R1 Pzt 09-17 + Çar 09-12 → 5×480 + 4×180 = 3120 dk; VET2@R2 Salı 10-14 (10 Mart'tan
 *       itibaren → 4×240) + Per 09-10 (12 Mart'a kadar → 2×60) = 1080 dk, pasif Cuma satırı sayılmaz;
 *       VET3@R3 Pzt 09-11 → 5×120 = 600 dk.</li>
 *   <li>Randevular: R1 7 (COMPLETED 2, CANCELLED, NO_SHOW, BOOKED, CONFIRMED, hekimsiz REQUESTED), dolu 140 dk →
 *       %4,5; R2 2 (COMPLETED, IN_PROGRESS) 90 dk → %8,3; R3 1 COMPLETED 60 dk → %10,0; toplam 290/4800 → %6,0.
 *       Şubat ve Nisan'daki randevular aralık dışıdır.</li>
 *   <li>Muayeneler: R1 3 (2 Mart CONSULTATION + VACCINATION, 9 Mart CONSULTATION), R2 2 (10 Mart ve
 *       3 Mart 01:00 yerel = 2 Mart UTC), R3 1 (2 Mart EMERGENCY). 1 Nisan 00:30 yerel ve Şubat aralık dışı.</li>
 *   <li>Satış (KDV dahil): R1 i1 710 + i2 990 (ödendi) + i3 600 (200 tahsil) = 2300; R2 i6 220; R3 i7 120 →
 *       5 fatura, 2640 TL, tahsil 1190. Taslak, iptal, proforma ve Şubat faturası sayılmaz.</li>
 *   <li>Stok (POSTED): Amoksisilin R1 alım +100, satış −3 −2, transfer çıkış −10; R2 transfer giriş +10.
 *       Kedi maması R1 alım +20, sayım −1; R3 alım +7. Taslak ve Şubat hareketi sayılmaz.</li>
 *   <li>Aşılar (bugüne göre): gecikmiş R1 Karma (10 gün), R2 Karma (40 gün), R3 Kuduz (3 gün); sonradan yenilenmiş
 *       Kuduz ve ölen hastanın aşısı sayılmaz. R1'de 7 gün sonra Lepto (yaklaşan).</li>
 *   <li>Bugün: R1'de 00:01 BOOKED ve iptal randevu, R2'de bir randevu; R1'de açık (ADMISSION) bir muayene.</li>
 * </ul>
 */
public final class ReportTestData {

    public static final ZoneId ZONE = ZoneId.of("Europe/Istanbul");
    public static final String MARCH_FROM = "2026-03-01T00:00:00+03:00";
    public static final String MARCH_TO = "2026-04-01T00:00:00+03:00";

    public static final UUID ORG_R = uuid(0x310001);
    public static final UUID ORG_Q = uuid(0x310002);
    public static final int ORG_R_CODE = 9731;
    public static final int ORG_Q_CODE = 9732;

    public static final UUID GROUP_RG1 = uuid(0x310011);
    public static final UUID GROUP_RG2 = uuid(0x310012);
    public static final UUID GROUP_QG = uuid(0x310013);
    public static final UUID R1 = uuid(0x310021);
    public static final UUID R2 = uuid(0x310022);
    public static final UUID R3 = uuid(0x310023);
    public static final UUID Q1 = uuid(0x310024);

    public static final UUID ADMIN = uuid(0x310031);
    public static final UUID MANAGER_R2 = uuid(0x310032);
    public static final UUID RECEPTION = uuid(0x310033);
    public static final UUID ADMIN_Q = uuid(0x310034);
    public static final String EMAIL_ADMIN = "rp-admin@test.com";
    public static final String EMAIL_MANAGER_R2 = "rp-manager@test.com";
    public static final String EMAIL_RECEPTION = "rp-reception@test.com";
    public static final String EMAIL_ADMIN_Q = "rp-admin-q@test.com";
    public static final String NAME_ADMIN = "Rapor Yöneticisi";
    public static final String NAME_MANAGER_R2 = "Şube Müdürü R2";

    public static final UUID VET1 = uuid(0x310041);
    public static final UUID VET2 = uuid(0x310042);
    public static final UUID VET3 = uuid(0x310043);
    public static final UUID VET_Q = uuid(0x310044);
    public static final String VET1_NAME = "Dr. Ayşe Kaya";

    public static final UUID CLIENT1 = uuid(0x310051);
    public static final UUID CLIENT2 = uuid(0x310052);
    public static final UUID CLIENT3 = uuid(0x310053);
    public static final UUID CLIENT_Q = uuid(0x310054);
    public static final String CLIENT1_NAME = "Ali Veli";
    public static final String CLIENT1_PHONE = "+905550000001";
    public static final UUID PET1 = uuid(0x310061);
    public static final UUID PET2 = uuid(0x310062);
    public static final UUID PET3 = uuid(0x310063);
    public static final UUID PET_DECEASED = uuid(0x310064);
    public static final UUID PET_Q = uuid(0x310065);

    public static final UUID ENC_R1_CONSULT = uuid(0x310071);
    public static final UUID ENC_R1_VACCINE = uuid(0x310072);
    public static final UUID ENC_R1_DECEASED = uuid(0x310073);
    public static final UUID ENC_R2 = uuid(0x310074);
    public static final UUID ENC_R3 = uuid(0x310075);
    public static final UUID ENC_OPEN_TODAY = uuid(0x310078);
    public static final UUID ENC_Q = uuid(0x31007b);

    public static final UUID VR_KARMA_R1 = uuid(0x310083);
    public static final UUID VR_KARMA_R2 = uuid(0x310084);
    public static final UUID VR_KUDUZ_R3 = uuid(0x310086);
    public static final UUID VR_LEPTO_R1 = uuid(0x310087);

    public static final UUID PRODUCT_AMOX = uuid(0x310091);
    public static final UUID PRODUCT_FOOD = uuid(0x310092);
    public static final UUID SERVICE_EXAM = uuid(0x310093);
    public static final UUID APPT_TODAY_R1 = uuid(0x3100a1);

    private static final UUID ROLE_ADMIN_R = uuid(0x310101);
    private static final UUID ROLE_RECEPTION_R = uuid(0x310102);
    private static final UUID ROLE_ADMIN_Q = uuid(0x310103);

    private static final String[] ADMIN_PERMISSIONS = {"clients.read", "clients.write", "appointments.manage",
            "clinical.read", "clinical.write", "inventory.read", "inventory.write", "billing.manage", "users.manage",
            "reports.read", "audit.read"};
    /** V22 sistem "receptionist" rolünün yetkileri. */
    private static final String[] RECEPTION_PERMISSIONS = {"clients.read", "clients.write", "appointments.manage",
            "inventory.read", "inventory.write", "billing.manage"};

    private static final DateTimeFormatter LOCAL = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static boolean seeded;
    private static int seq;

    private ReportTestData() {
    }

    public static synchronized void seed(DataSource superUser) {
        if (seeded) {
            return;
        }
        try (Connection c = superUser.getConnection()) {
            c.setAutoCommit(false);
            // Tetikleyiciler (erişim ataması, çalışma saati, fatura/stok kuralları) kapatılır: veri doğrudan kurulur.
            exec(c, "SET LOCAL session_replication_role = replica");
            structure(c);
            people(c);
            workingHours(c);
            appointments(c);
            encounters(c);
            vaccines(c);
            stock(c);
            invoices(c);
            c.commit();
            seeded = true;
        } catch (SQLException ex) {
            throw new IllegalStateException("Rapor test verisi yüklenemedi: " + ex.getMessage(), ex);
        }
    }

    /** Bu sınıfın iki organizasyonuna ait tüm satırlar silinir (immutable tablolar için tetikleyiciler kapatılır). */
    public static synchronized void cleanup(DataSource superUser) {
        if (!seeded) {
            return;
        }
        try (Connection c = superUser.getConnection()) {
            c.setAutoCommit(false);
            exec(c, "SET LOCAL session_replication_role = replica");
            List<String> tables = new ArrayList<>();
            try (var st = c.createStatement(); ResultSet rs = st.executeQuery("""
                    select c.table_name from information_schema.columns c
                    join information_schema.tables t on t.table_schema = c.table_schema and t.table_name = c.table_name
                    where c.table_schema = 'vet' and c.column_name = 'organization_id' and t.table_type = 'BASE TABLE'
                    """)) {
                while (rs.next()) {
                    tables.add(rs.getString(1));
                }
            }
            for (String table : tables) {
                exec(c, "DELETE FROM vet." + table + " WHERE organization_id IN (?, ?)", ORG_R, ORG_Q);
            }
            exec(c, "DELETE FROM vet.organizations WHERE id IN (?, ?)", ORG_R, ORG_Q);
            c.commit();
            seeded = false;
        } catch (SQLException ex) {
            throw new IllegalStateException("Rapor test verisi temizlenemedi: " + ex.getMessage(), ex);
        }
    }

    /** Türkiye saatiyle "yyyy-MM-dd HH:mm". */
    public static OffsetDateTime at(String local) {
        return LocalDateTime.parse(local, LOCAL).atZone(ZONE).toOffsetDateTime();
    }

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    // ---------------------------------------------------------------- kurulum

    private static void structure(Connection c) throws SQLException {
        org(c, ORG_R, ORG_R_CODE);
        org(c, ORG_Q, ORG_Q_CODE);
        exec(c, "INSERT INTO vet.clinic_groups (id, organization_id, code, name) VALUES (?, ?, 'RG1', 'RG1'), (?, ?, 'RG2', 'RG2'), (?, ?, 'QG', 'QG')",
                GROUP_RG1, ORG_R, GROUP_RG2, ORG_R, GROUP_QG, ORG_Q);
        branch(c, R1, ORG_R, GROUP_RG1, "R1", "R-Merkez");
        branch(c, R2, ORG_R, GROUP_RG1, "R2", "R-Kadıköy");
        branch(c, R3, ORG_R, GROUP_RG2, "R3", "R-Ankara");
        branch(c, Q1, ORG_Q, GROUP_QG, "Q1", "Q-Merkez");
        role(c, ROLE_ADMIN_R, ORG_R, "rp-admin", ADMIN_PERMISSIONS);
        role(c, ROLE_RECEPTION_R, ORG_R, "rp-reception", RECEPTION_PERMISSIONS);
        role(c, ROLE_ADMIN_Q, ORG_Q, "rp-admin", ADMIN_PERMISSIONS);
        user(c, ADMIN, ORG_R, EMAIL_ADMIN, NAME_ADMIN);
        assign(c, ORG_R, ADMIN, R1, ROLE_ADMIN_R);
        assign(c, ORG_R, ADMIN, R2, ROLE_ADMIN_R);
        assign(c, ORG_R, ADMIN, R3, ROLE_ADMIN_R);
        user(c, MANAGER_R2, ORG_R, EMAIL_MANAGER_R2, NAME_MANAGER_R2);
        assign(c, ORG_R, MANAGER_R2, R2, ROLE_ADMIN_R);
        user(c, RECEPTION, ORG_R, EMAIL_RECEPTION, "Resepsiyon R1");
        assign(c, ORG_R, RECEPTION, R1, ROLE_RECEPTION_R);
        user(c, ADMIN_Q, ORG_Q, EMAIL_ADMIN_Q, "Q Yöneticisi");
        assign(c, ORG_Q, ADMIN_Q, Q1, ROLE_ADMIN_Q);
        vet(c, VET1, ORG_R, VET1_NAME);
        vet(c, VET2, ORG_R, "Dr. Mehmet Demir");
        vet(c, VET3, ORG_R, "Dr. Zeynep Ak");
        vet(c, VET_Q, ORG_Q, "Dr. Q");
    }

    private static void people(Connection c) throws SQLException {
        client(c, CLIENT1, ORG_R, CLIENT1_NAME, R1);
        exec(c, "INSERT INTO vet.client_contacts (organization_id, client_id, contact_type, contact_value, is_primary) VALUES (?, ?, 'PHONE', ?, true)",
                ORG_R, CLIENT1, CLIENT1_PHONE);
        client(c, CLIENT2, ORG_R, "Ayşe Yıldız", R2);
        client(c, CLIENT3, ORG_R, "Can Er", R3);
        client(c, CLIENT_Q, ORG_Q, "Q Sahip", Q1);
        animal(c, PET1, ORG_R, "Karabaş", "DOG", CLIENT1, R1, null);
        animal(c, PET2, ORG_R, "Pamuk", "CAT", CLIENT2, R2, null);
        animal(c, PET3, ORG_R, "Boncuk", "CAT", CLIENT3, R3, null);
        animal(c, PET_DECEASED, ORG_R, "Eski", "DOG", CLIENT1, R1, LocalDate.of(2026, 3, 20));
        animal(c, PET_Q, ORG_Q, "Q Hasta", "DOG", CLIENT_Q, Q1, null);
    }

    private static void workingHours(Connection c) throws SQLException {
        hours(c, ORG_R, R1, VET1, 1, "09:00", "17:00", LocalDate.of(2026, 1, 1), null, true);
        hours(c, ORG_R, R1, VET1, 3, "09:00", "12:00", LocalDate.of(2026, 1, 1), null, true);
        hours(c, ORG_R, R2, VET2, 2, "10:00", "14:00", LocalDate.of(2026, 3, 10), null, true);
        hours(c, ORG_R, R2, VET2, 4, "09:00", "10:00", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 12), true);
        hours(c, ORG_R, R2, VET2, 5, "09:00", "17:00", LocalDate.of(2026, 1, 1), null, false);
        hours(c, ORG_R, R3, VET3, 1, "09:00", "11:00", LocalDate.of(2026, 1, 1), null, true);
        hours(c, ORG_Q, Q1, VET_Q, 1, "09:00", "17:00", LocalDate.of(2026, 1, 1), null, true);
    }

    private static void appointments(Connection c) throws SQLException {
        appt(c, uuid(0x310201), ORG_R, R1, PET1, CLIENT1, VET1, "COMPLETED", at("2026-03-02 09:00"), at("2026-03-02 09:30"));
        appt(c, uuid(0x310202), ORG_R, R1, PET1, CLIENT1, VET1, "COMPLETED", at("2026-03-02 10:00"), at("2026-03-02 11:00"));
        appt(c, uuid(0x310203), ORG_R, R1, PET1, CLIENT1, VET1, "CANCELLED", at("2026-03-04 09:00"), at("2026-03-04 09:30"));
        appt(c, uuid(0x310204), ORG_R, R1, PET1, CLIENT1, VET1, "NO_SHOW", at("2026-03-09 09:00"), at("2026-03-09 09:45"));
        appt(c, uuid(0x310205), ORG_R, R1, PET1, CLIENT1, VET1, "BOOKED", at("2026-03-09 10:00"), at("2026-03-09 10:30"));
        appt(c, uuid(0x310206), ORG_R, R1, PET1, CLIENT1, VET1, "CONFIRMED", at("2026-03-16 09:00"), at("2026-03-16 09:20"));
        appt(c, uuid(0x310207), ORG_R, R1, PET1, CLIENT1, null, "REQUESTED", at("2026-03-03 11:00"), at("2026-03-03 11:30"));
        appt(c, uuid(0x310208), ORG_R, R1, PET1, CLIENT1, VET1, "COMPLETED", at("2026-02-28 16:00"), at("2026-02-28 16:30"));
        appt(c, uuid(0x310209), ORG_R, R1, PET1, CLIENT1, VET1, "COMPLETED", at("2026-04-01 00:10"), at("2026-04-01 00:30"));
        appt(c, uuid(0x310211), ORG_R, R2, PET2, CLIENT2, VET2, "COMPLETED", at("2026-03-10 10:00"), at("2026-03-10 11:00"));
        appt(c, uuid(0x310212), ORG_R, R2, PET2, CLIENT2, VET2, "IN_PROGRESS", at("2026-03-17 10:00"), at("2026-03-17 10:30"));
        appt(c, uuid(0x310221), ORG_R, R3, PET3, CLIENT3, VET3, "COMPLETED", at("2026-03-02 09:00"), at("2026-03-02 10:00"));
        appt(c, uuid(0x310231), ORG_Q, Q1, PET_Q, CLIENT_Q, VET_Q, "COMPLETED", at("2026-03-02 09:00"), at("2026-03-02 13:00"));
        // Bugün (genel bakış): R1'de bir aktif ve bir iptal randevu, R2'de bir randevu.
        OffsetDateTime todayStart = today().atStartOfDay(ZONE).toOffsetDateTime();
        appt(c, APPT_TODAY_R1, ORG_R, R1, PET1, CLIENT1, VET1, "BOOKED", todayStart.plusMinutes(1), todayStart.plusMinutes(21));
        appt(c, uuid(0x3100a2), ORG_R, R1, PET1, CLIENT1, VET1, "CANCELLED", todayStart.plusMinutes(1), todayStart.plusMinutes(21));
        appt(c, uuid(0x3100a3), ORG_R, R2, PET2, CLIENT2, VET2, "BOOKED", todayStart.plusMinutes(1), todayStart.plusMinutes(21));
    }

    private static void encounters(Connection c) throws SQLException {
        enc(c, ENC_R1_CONSULT, ORG_R, R1, PET1, VET1, "CONSULTATION", "CLOSED", at("2026-03-02 09:05"));
        enc(c, ENC_R1_VACCINE, ORG_R, R1, PET1, VET1, "VACCINATION", "CLOSED", at("2026-03-02 10:05"));
        enc(c, ENC_R1_DECEASED, ORG_R, R1, PET_DECEASED, VET1, "CONSULTATION", "CLOSED", at("2026-03-09 10:00"));
        enc(c, ENC_R2, ORG_R, R2, PET2, VET2, "CONSULTATION", "CLOSED", at("2026-03-10 10:05"));
        enc(c, uuid(0x310076), ORG_R, R2, PET2, VET2, "CONSULTATION", "CLOSED", at("2026-03-03 01:00"));
        enc(c, ENC_R3, ORG_R, R3, PET3, VET3, "EMERGENCY", "CLOSED", at("2026-03-02 09:10"));
        enc(c, uuid(0x310077), ORG_R, R1, PET1, VET1, "CONSULTATION", "CLOSED", at("2026-02-27 10:00"));
        enc(c, uuid(0x310079), ORG_R, R2, PET2, VET2, "CONSULTATION", "CLOSED", at("2026-04-01 00:30"));
        enc(c, ENC_OPEN_TODAY, ORG_R, R1, PET1, VET1, "FOLLOW_UP", "ADMISSION", OffsetDateTime.now(ZONE).minusMinutes(1));
        enc(c, ENC_Q, ORG_Q, Q1, PET_Q, VET_Q, "CONSULTATION", "CLOSED", at("2026-03-02 09:05"));
    }

    private static void vaccines(Connection c) throws SQLException {
        LocalDate t = today();
        vaccine(c, uuid(0x310081), ORG_R, R1, PET1, ENC_R1_VACCINE, VET1, "Kuduz", t.minusDays(400), t.minusDays(35));
        vaccine(c, uuid(0x310082), ORG_R, R1, PET1, ENC_R1_VACCINE, VET1, "Kuduz", t.minusDays(30), t.plusDays(335));
        vaccine(c, VR_KARMA_R1, ORG_R, R1, PET1, ENC_R1_VACCINE, VET1, "Karma", t.minusDays(60), t.minusDays(10));
        vaccine(c, VR_KARMA_R2, ORG_R, R2, PET2, ENC_R2, VET2, "Karma", t.minusDays(50), t.minusDays(40));
        vaccine(c, uuid(0x310085), ORG_R, R1, PET_DECEASED, ENC_R1_DECEASED, VET1, "Kuduz", t.minusDays(100), t.minusDays(5));
        vaccine(c, VR_KUDUZ_R3, ORG_R, R3, PET3, ENC_R3, VET3, "Kuduz", t.minusDays(90), t.minusDays(3));
        vaccine(c, VR_LEPTO_R1, ORG_R, R1, PET1, ENC_R1_VACCINE, VET1, "Lepto", t.minusDays(20), t.plusDays(7));
        vaccine(c, uuid(0x310088), ORG_R, R1, PET1, ENC_R1_VACCINE, VET1, "Parazit", t.minusDays(20), t.plusDays(30));
        vaccine(c, uuid(0x310089), ORG_Q, Q1, PET_Q, ENC_Q, VET_Q, "Kuduz", t.minusDays(90), t.minusDays(3));
    }

    private static void stock(Connection c) throws SQLException {
        product(c, PRODUCT_AMOX, ORG_R, "RP-AMX", "Amoksisilin", "MEDICATION", 200);
        product(c, PRODUCT_FOOD, ORG_R, "RP-FOOD", "Kedi Maması", "FOOD", 0);
        product(c, uuid(0x310094), ORG_Q, "RP-Q", "Q Ürün", "MEDICATION", 0);
        UUID l1 = location(c, uuid(0x310301), ORG_R, R1);
        UUID l2 = location(c, uuid(0x310302), ORG_R, R2);
        UUID l3 = location(c, uuid(0x310303), ORG_R, R3);
        UUID lq = location(c, uuid(0x310304), ORG_Q, Q1);
        tx(c, ORG_R, l1, "RECEIPT", "POSTED", at("2026-03-03 10:00"), line(PRODUCT_AMOX, "IN", 100), line(PRODUCT_FOOD, "IN", 20));
        tx(c, ORG_R, l1, "DISPENSE", "POSTED", at("2026-03-05 10:00"), line(PRODUCT_AMOX, "OUT", 3));
        tx(c, ORG_R, l1, "DISPENSE", "POSTED", at("2026-03-06 10:00"), line(PRODUCT_AMOX, "OUT", 2));
        tx(c, ORG_R, l1, "ADJUSTMENT", "POSTED", at("2026-03-07 10:00"), line(PRODUCT_FOOD, "OUT", 1));
        tx(c, ORG_R, l1, "TRANSFER_OUT", "POSTED", at("2026-03-08 10:00"), line(PRODUCT_AMOX, "OUT", 10));
        tx(c, ORG_R, l2, "TRANSFER_IN", "POSTED", at("2026-03-08 10:00"), line(PRODUCT_AMOX, "IN", 10));
        tx(c, ORG_R, l1, "RECEIPT", "DRAFT", at("2026-03-09 10:00"), line(PRODUCT_AMOX, "IN", 50));
        tx(c, ORG_R, l1, "RECEIPT", "POSTED", at("2026-02-20 10:00"), line(PRODUCT_AMOX, "IN", 5));
        tx(c, ORG_R, l3, "RECEIPT", "POSTED", at("2026-03-10 10:00"), line(PRODUCT_FOOD, "IN", 7));
        tx(c, ORG_Q, lq, "RECEIPT", "POSTED", at("2026-03-10 10:00"), line(uuid(0x310094), "IN", 99));
    }

    private static void invoices(Connection c) throws SQLException {
        exec(c, "INSERT INTO vet.service_catalog (id, organization_id, code, name, default_price, tax_rate) VALUES (?, ?, 'RP-MUAYENE', 'Genel muayene', 500, 20)",
                SERVICE_EXAM, ORG_R);
        UUID i1 = invoice(c, ORG_R, R1, CLIENT1, "SALES", "ISSUED", at("2026-03-02 12:00"));
        serviceLine(c, ORG_R, i1, 1, 1, "500", "20");
        productLine(c, ORG_R, i1, 2, PRODUCT_AMOX, "2", "50", "0", "10");
        UUID i2 = invoice(c, ORG_R, R1, CLIENT1, "SALES", "PAID", at("2026-03-02 15:00"));
        productLine(c, ORG_R, i2, 1, PRODUCT_FOOD, "1", "1000", "100", "10");
        payment(c, ORG_R, R1, CLIENT1, i2, "COMPLETED", "990");
        UUID i3 = invoice(c, ORG_R, R1, CLIENT1, "SALES", "PARTIALLY_PAID", at("2026-03-09 12:00"));
        serviceLine(c, ORG_R, i3, 1, 1, "500", "20");
        payment(c, ORG_R, R1, CLIENT1, i3, "COMPLETED", "200");
        payment(c, ORG_R, R1, CLIENT1, i3, "VOID", "100");
        UUID draft = invoice(c, ORG_R, R1, CLIENT1, "SALES", "DRAFT", null);
        serviceLine(c, ORG_R, draft, 1, 1, "500", "20");
        UUID voided = invoice(c, ORG_R, R1, CLIENT1, "SALES", "VOID", at("2026-03-11 12:00"));
        productLine(c, ORG_R, voided, 1, PRODUCT_AMOX, "1", "50", "0", "10");
        UUID proforma = invoice(c, ORG_R, R1, CLIENT1, "PROFORMA", "ISSUED", at("2026-03-12 12:00"));
        serviceLine(c, ORG_R, proforma, 1, 1, "500", "20");
        UUID february = invoice(c, ORG_R, R1, CLIENT1, "SALES", "ISSUED", at("2026-02-27 12:00"));
        serviceLine(c, ORG_R, february, 1, 1, "500", "20");
        UUID i6 = invoice(c, ORG_R, R2, CLIENT2, "SALES", "OVERDUE", at("2026-03-10 13:00"));
        productLine(c, ORG_R, i6, 1, PRODUCT_AMOX, "4", "50", "0", "10");
        UUID i7 = invoice(c, ORG_R, R3, CLIENT3, "SALES", "ISSUED", at("2026-03-02 11:00"));
        exec(c, "INSERT INTO vet.invoice_lines (organization_id, invoice_id, line_no, line_type, description, quantity, unit_price, tax_rate) VALUES (?, ?, 1, 'OTHER', 'Kayıt ücreti', 1, 100, 20)",
                ORG_R, i7);
        UUID iq = invoice(c, ORG_Q, Q1, CLIENT_Q, "SALES", "ISSUED", at("2026-03-02 12:00"));
        exec(c, "INSERT INTO vet.invoice_lines (organization_id, invoice_id, line_no, line_type, description, quantity, unit_price, tax_rate) VALUES (?, ?, 1, 'OTHER', 'Q kalem', 1, 9999, 20)",
                ORG_Q, iq);
    }

    // ---------------------------------------------------------------- denetim izi

    /** Doğrudan denetim kaydı (test için; occurred_at verilir). */
    public static void audit(DataSource superUser, UUID org, UUID clinic, UUID actor, String action, String entityType,
                             UUID entityId, OffsetDateTime occurredAt) {
        try (Connection c = superUser.getConnection()) {
            exec(c, "INSERT INTO vet.audit_logs (organization_id, clinic_id, actor_user_id, action, entity_type, entity_id, occurred_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    org, clinic, actor, action, entityType, entityId, occurredAt);
        } catch (SQLException ex) {
            throw new IllegalStateException(ex.getMessage(), ex);
        }
    }

    /** Aynı anda {@code count} denetim kaydı (500 sınırı testi). */
    public static void bulkAudit(DataSource superUser, UUID org, UUID clinic, UUID actor, String action, int count,
                                 OffsetDateTime start) {
        try (Connection c = superUser.getConnection()) {
            exec(c, "INSERT INTO vet.audit_logs (organization_id, clinic_id, actor_user_id, action, entity_type, occurred_at) "
                    + "SELECT ?, ?, ?, ?, 'PRODUCT', ?::timestamptz + make_interval(secs => g) FROM generate_series(1, ?) g",
                    org, clinic, actor, action, start, count);
        } catch (SQLException ex) {
            throw new IllegalStateException(ex.getMessage(), ex);
        }
    }

    // ---------------------------------------------------------------- yardımcılar

    private static void org(Connection c, UUID id, int code) throws SQLException {
        exec(c, "INSERT INTO vet.organizations (id, code, name, legal_name, default_currency, timezone_name, is_active) VALUES (?, ?, ?, ?, 'TRY', 'Europe/Istanbul', true)",
                id, code, "ORG" + code, "ORG" + code);
    }

    private static void branch(Connection c, UUID id, UUID org, UUID group, String code, String name) throws SQLException {
        exec(c, "INSERT INTO vet.clinics (id, organization_id, clinic_group_id, code, name) VALUES (?, ?, ?, ?, ?)", id, org, group, code, name);
    }

    private static void role(Connection c, UUID id, UUID org, String code, String... permissions) throws SQLException {
        exec(c, "INSERT INTO vet.roles (id, organization_id, code, name, is_system_role) VALUES (?, ?, ?, ?, false)", id, org, code, code);
        for (String p : permissions) {
            exec(c, "INSERT INTO vet.role_permissions (organization_id, role_id, permission_id) SELECT ?, ?, id FROM vet.permissions WHERE code = ?", org, id, p);
        }
    }

    private static void user(Connection c, UUID id, UUID org, String email, String name) throws SQLException {
        exec(c, "INSERT INTO vet.app_users (id, organization_id, email, password_hash, display_name, is_active, mfa_enabled) VALUES (?, ?, ?, vet.crypt(?, vet.gen_salt('bf', 4)), ?, true, false)",
                id, org, email, PASSWORD, name);
    }

    private static void assign(Connection c, UUID org, UUID user, UUID clinic, UUID role) throws SQLException {
        exec(c, "INSERT INTO vet.user_clinic_roles (organization_id, user_id, clinic_id, role_id, valid_from) VALUES (?, ?, ?, ?, CURRENT_DATE - 1)",
                org, user, clinic, role);
    }

    private static void vet(Connection c, UUID id, UUID org, String name) throws SQLException {
        UUID staff = UUID.randomUUID();
        exec(c, "INSERT INTO vet.staff_members (id, organization_id, full_name) VALUES (?, ?, ?)", staff, org, name);
        exec(c, "INSERT INTO vet.veterinarians (id, organization_id, staff_member_id, license_no) VALUES (?, ?, ?, ?)",
                id, org, staff, "RP-" + id.toString().substring(28));
    }

    private static void client(Connection c, UUID id, UUID org, String name, UUID clinic) throws SQLException {
        exec(c, "INSERT INTO vet.clients (id, organization_id, display_name) VALUES (?, ?, ?)", id, org, name);
        exec(c, "INSERT INTO vet.client_clinic_access (organization_id, client_id, clinic_id) VALUES (?, ?, ?)", org, id, clinic);
    }

    private static void animal(Connection c, UUID id, UUID org, String name, String species, UUID owner, UUID clinic,
                               LocalDate deceasedAt) throws SQLException {
        exec(c, "INSERT INTO vet.animals (id, organization_id, patient_no, name, species_id, deceased_at) SELECT ?, ?, ?, ?, id, ? FROM vet.species WHERE code = ?",
                id, org, "RP-" + id.toString().substring(28), name, deceasedAt, species);
        exec(c, "INSERT INTO vet.animal_clinic_access (organization_id, animal_id, clinic_id) VALUES (?, ?, ?)", org, id, clinic);
        exec(c, "INSERT INTO vet.animal_owners (organization_id, animal_id, client_id, is_primary_contact) VALUES (?, ?, ?, true)", org, id, owner);
    }

    private static void hours(Connection c, UUID org, UUID clinic, UUID vet, int dow, String from, String to,
                              LocalDate effectiveFrom, LocalDate effectiveTo, boolean active) throws SQLException {
        exec(c, "INSERT INTO vet.veterinarian_working_hours (organization_id, clinic_id, veterinarian_id, day_of_week, starts_at, ends_at, effective_from, effective_to, is_active) "
                        + "VALUES (?, ?, ?, ?, ?::time, ?::time, ?, ?, ?)",
                org, clinic, vet, dow, from, to, effectiveFrom, effectiveTo, active);
    }

    private static void appt(Connection c, UUID id, UUID org, UUID clinic, UUID animal, UUID client, UUID vet,
                             String status, OffsetDateTime starts, OffsetDateTime ends) throws SQLException {
        exec(c, "INSERT INTO vet.appointments (id, organization_id, clinic_id, client_id, animal_id, veterinarian_id, appointment_no, status, starts_at, ends_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, org, clinic, client, animal, vet, "RP-" + id.toString().substring(28), status, starts, ends);
    }

    private static void enc(Connection c, UUID id, UUID org, UUID clinic, UUID animal, UUID vet, String type,
                            String status, OffsetDateTime startedAt) throws SQLException {
        exec(c, "INSERT INTO vet.encounters (id, organization_id, clinic_id, animal_id, attending_vet_id, encounter_no, encounter_type, status, started_at, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, org, clinic, animal, vet, "RP-" + id.toString().substring(28), type, status, startedAt, startedAt);
    }

    private static void vaccine(Connection c, UUID id, UUID org, UUID clinic, UUID animal, UUID encounter, UUID vet,
                                String name, LocalDate administered, LocalDate nextDue) throws SQLException {
        exec(c, "INSERT INTO vet.vaccine_records (id, organization_id, clinic_id, animal_id, encounter_id, administered_by, vaccine_name, administered_at, next_due_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, org, clinic, animal, encounter, vet, name, administered.atTime(10, 0).atZone(ZONE).toOffsetDateTime(), nextDue);
    }

    private static void product(Connection c, UUID id, UUID org, String sku, String name, String type, int minimum)
            throws SQLException {
        exec(c, "INSERT INTO vet.products (id, organization_id, sku, name, product_type, tracks_lot, tracks_expiry, minimum_stock, sale_price) VALUES (?, ?, ?, ?, ?, false, false, ?, 50)",
                id, org, sku, name, type, minimum);
    }

    private static UUID location(Connection c, UUID id, UUID org, UUID clinic) throws SQLException {
        exec(c, "INSERT INTO vet.inventory_locations (id, organization_id, clinic_id, code, name) VALUES (?, ?, ?, 'MAIN', 'Ana depo')", id, org, clinic);
        return id;
    }

    private record Line(UUID product, String direction, int quantity) {
    }

    private static Line line(UUID product, String direction, int quantity) {
        return new Line(product, direction, quantity);
    }

    private static void tx(Connection c, UUID org, UUID location, String type, String status, OffsetDateTime at,
                           Line... lines) throws SQLException {
        UUID id = UUID.randomUUID();
        exec(c, "INSERT INTO vet.inventory_transactions (id, organization_id, location_id, transaction_no, transaction_type, status, occurred_at, posted_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id, org, location, "RP-TX-" + (++seq), type, status, at, "POSTED".equals(status) ? at : null);
        for (Line l : lines) {
            exec(c, "INSERT INTO vet.inventory_transaction_lines (organization_id, transaction_id, product_id, direction, quantity) VALUES (?, ?, ?, ?, ?)",
                    org, id, l.product(), l.direction(), l.quantity());
        }
    }

    private static UUID invoice(Connection c, UUID org, UUID clinic, UUID client, String type, String status,
                                OffsetDateTime issuedAt) throws SQLException {
        UUID id = UUID.randomUUID();
        exec(c, "INSERT INTO vet.invoices (id, organization_id, clinic_id, client_id, invoice_no, invoice_type, status, issued_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id, org, clinic, client, "RP-INV-" + (++seq), type, status, issuedAt);
        return id;
    }

    private static void serviceLine(Connection c, UUID org, UUID invoice, int no, int qty, String price, String tax)
            throws SQLException {
        exec(c, "INSERT INTO vet.invoice_lines (organization_id, invoice_id, line_no, line_type, service_id, description, quantity, unit_price, tax_rate) "
                        + "VALUES (?, ?, ?, 'SERVICE', ?, 'Muayene', ?, ?::numeric, ?::numeric)",
                org, invoice, no, SERVICE_EXAM, qty, price, tax);
    }

    private static void productLine(Connection c, UUID org, UUID invoice, int no, UUID product, String qty, String price,
                                    String discount, String tax) throws SQLException {
        exec(c, "INSERT INTO vet.invoice_lines (organization_id, invoice_id, line_no, line_type, product_id, description, quantity, unit_price, discount_amount, tax_rate) "
                        + "VALUES (?, ?, ?, 'PRODUCT', ?, 'Ürün', ?::numeric, ?::numeric, ?::numeric, ?::numeric)",
                org, invoice, no, product, qty, price, discount, tax);
    }

    private static void payment(Connection c, UUID org, UUID clinic, UUID client, UUID invoice, String status,
                                String amount) throws SQLException {
        UUID id = UUID.randomUUID();
        exec(c, "INSERT INTO vet.payments (id, organization_id, client_id, clinic_id, payment_no, payment_method, status, amount) VALUES (?, ?, ?, ?, ?, 'CASH', ?, ?::numeric)",
                id, org, client, clinic, "RP-PAY-" + (++seq), status, amount);
        exec(c, "INSERT INTO vet.invoice_payments (organization_id, invoice_id, payment_id, applied_amount) VALUES (?, ?, ?, ?::numeric)",
                org, invoice, id, amount);
    }

    private static void exec(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            ps.execute();
        }
    }
}
