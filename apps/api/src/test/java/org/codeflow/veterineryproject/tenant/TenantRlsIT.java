package org.codeflow.veterineryproject.tenant;

import org.codeflow.veterineryproject.common.test.PostgresContainerSupport;
import org.codeflow.veterineryproject.common.test.TenantTestData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.codeflow.veterineryproject.common.test.TenantTestData.*;

/**
 * RLS kurallarını, RLS'e tabi vet_app rolüyle doğrular (superuser RLS'i atlar).
 * Kural 1: aynı klinikteki şubeler birbirinin verisini görür/değiştirir.
 * Kural 2: farklı klinikler görmez. Kural 3: farklı organizasyonlar görmez.
 * Kural 4: context yoksa 0 satır (hata yok). Kural 5: platform admin yalnızca SELECT.
 */
class TenantRlsIT extends PostgresContainerSupport {

    @BeforeAll
    static void seed() {
        TenantTestData.seed(superUserDataSource());
    }

    // ---------- Kural 1: aynı klinikin şubeleri ----------

    @Test
    void rule1_select_branchUserSeesPatientsAndAppointmentsOfSiblingBranch() throws Exception {
        Ctx ctx = Ctx.tenant(ORG_A, USER_A1_S1, BRANCH_A1_S1);

        assertThat(count("animals", PATIENT_A1_S2, ctx)).isEqualTo(1);
        assertThat(count("clients", PATIENT_A1_S2, ctx)).isEqualTo(1);
        assertThat(count("appointments", APPT_A1_S2, ctx)).isEqualTo(1);
        assertThat(count("animals", PATIENT_A1_S1, ctx)).isEqualTo(1);
    }

    @Test
    void rule1_update_branchUserCanModifyPatientOfSiblingBranch() throws Exception {
        int updated = inTx(Ctx.tenant(ORG_A, USER_A1_S1, BRANCH_A1_S1),
                c -> update(c, "UPDATE vet.animals SET name = 'Yeni Ad' WHERE id = ?", PATIENT_A1_S2));

        assertThat(updated).isEqualTo(1);
    }

    @Test
    void rule1_insert_branchUserCanBookAppointmentForPatientRegisteredAtSiblingBranch() throws Exception {
        int inserted = inTx(Ctx.tenant(ORG_A, USER_A1_S1, BRANCH_A1_S1),
                c -> insertAppointment(c, UUID.randomUUID(), ORG_A, BRANCH_A1_S1, PATIENT_A1_S2, "APT-X1"));

        assertThat(inserted).isEqualTo(1);
    }

    @Test
    void rule1_delete_branchUserCanDeleteAppointmentOfSiblingBranch() throws Exception {
        int deleted = inTx(Ctx.tenant(ORG_A, USER_A1_S1, BRANCH_A1_S1),
                c -> update(c, "DELETE FROM vet.appointments WHERE id = ?", APPT_A1_S2));

        assertThat(deleted).isEqualTo(1);
    }

    // ---------- Kural 2: farklı klinikler ----------

    @Test
    void rule2_select_otherClinicDataIsInvisible_listAndById() throws Exception {
        Ctx ctx = Ctx.tenant(ORG_A, USER_A1_S1, BRANCH_A1_S1);

        assertThat(count("animals", PATIENT_A2_S1, ctx)).isZero();
        assertThat(count("clients", PATIENT_A2_S1, ctx)).isZero();
        assertThat(count("appointments", APPT_A2_S1, ctx)).isZero();
        assertThat(totalCount("appointments", ctx)).isEqualTo(2);
        assertThat(totalCount("animals", ctx)).isEqualTo(2);
        assertThat(totalCount("clients", ctx)).isEqualTo(2);
    }

    @Test
    void rule2_insert_cannotInsertAppointmentIntoOtherClinicBranch_withCheck() {
        assertThatThrownBy(() -> inTx(Ctx.tenant(ORG_A, USER_A1_S1, BRANCH_A1_S1),
                c -> insertAppointment(c, UUID.randomUUID(), ORG_A, BRANCH_A2_S1, PATIENT_A2_S1, "APT-X2")))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void rule2_insert_cannotInsertAppointmentIntoOtherClinicBranchEvenForOwnPatient() {
        assertThatThrownBy(() -> inTx(Ctx.tenant(ORG_A, USER_A1_S1, BRANCH_A1_S1),
                c -> insertAppointment(c, UUID.randomUUID(), ORG_A, BRANCH_A2_S1, PATIENT_A1_S1, "APT-X3")))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void rule2_insert_patientWithForeignClinicIdContextIsRejected() {
        assertThatThrownBy(() -> inTx(Ctx.tenant(ORG_A, USER_A1_S1, BRANCH_A2_S1),
                c -> update(c, "INSERT INTO vet.clients (id, organization_id, display_name) VALUES (?, ?, 'Sızma')", UUID.randomUUID(), ORG_A)))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void rule2_update_otherClinicRowsAffectZeroRows() throws Exception {
        Ctx ctx = Ctx.tenant(ORG_A, USER_A1_S1, BRANCH_A1_S1);

        assertThat((int) inTx(ctx, c -> update(c, "UPDATE vet.animals SET name = 'x' WHERE id = ?", PATIENT_A2_S1))).isZero();
        assertThat((int) inTx(ctx, c -> update(c, "UPDATE vet.appointments SET reason = 'x' WHERE id = ?", APPT_A2_S1))).isZero();
    }

    @Test
    void rule2_delete_otherClinicRowsAffectZeroRows() throws Exception {
        Ctx ctx = Ctx.tenant(ORG_A, USER_A1_S1, BRANCH_A1_S1);

        assertThat((int) inTx(ctx, c -> update(c, "DELETE FROM vet.appointments WHERE id = ?", APPT_A2_S1))).isZero();
        assertThat((int) inTx(ctx, c -> update(c, "DELETE FROM vet.animals WHERE id = ?", PATIENT_A2_S1))).isZero();
    }

    @Test
    void rule2_update_cannotMoveOwnAppointmentIntoOtherClinic_withCheck() {
        assertThatThrownBy(() -> inTx(Ctx.tenant(ORG_A, USER_A1_S1, BRANCH_A1_S1),
                c -> update(c, "UPDATE vet.appointments SET clinic_id = ? WHERE id = ?", BRANCH_A2_S1, APPT_A1_S1)))
                .isInstanceOf(SQLException.class);
    }

    // ---------- Kural 3: farklı organizasyonlar ----------

    @Test
    void rule3_select_otherOrganizationDataIsInvisible() throws Exception {
        Ctx ctx = Ctx.tenant(ORG_A, USER_A1_S1, BRANCH_A1_S1);

        assertThat(count("animals", PATIENT_B1_S1, ctx)).isZero();
        assertThat(count("appointments", APPT_B1_S1, ctx)).isZero();
        assertThat(count("organizations", ORG_B, ctx)).isZero();
        assertThat(count("clinics", BRANCH_B1_S1, ctx)).isZero();
    }

    @Test
    void rule3_select_orgAUserCannotReadOrgBEvenByClaimingOrgBContext() throws Exception {
        Ctx ctx = Ctx.tenant(ORG_B, USER_A1_S1, BRANCH_B1_S1);

        assertThat(count("animals", PATIENT_B1_S1, ctx)).isZero();
        assertThat(count("appointments", APPT_B1_S1, ctx)).isZero();
    }

    @Test
    void rule3_writeToOtherOrganizationAffectsZeroRowsOrIsRejected() throws Exception {
        Ctx ctx = Ctx.tenant(ORG_A, USER_A1_S1, BRANCH_A1_S1);

        assertThat((int) inTx(ctx, c -> update(c, "UPDATE vet.animals SET name = 'x' WHERE id = ?", PATIENT_B1_S1))).isZero();
        assertThat((int) inTx(ctx, c -> update(c, "DELETE FROM vet.appointments WHERE id = ?", APPT_B1_S1))).isZero();
        assertThatThrownBy(() -> inTx(ctx,
                c -> insertAppointment(c, UUID.randomUUID(), ORG_B, BRANCH_B1_S1, PATIENT_B1_S1, "APT-X4")))
                .isInstanceOf(SQLException.class);
    }

    // ---------- Kural 4: context yok / boş ----------

    @Test
    void rule4_noContext_selectReturnsZeroRowsWithoutError() throws Exception {
        Ctx ctx = Ctx.none();

        assertThat(totalCount("clients", ctx)).isZero();
        assertThat(totalCount("animals", ctx)).isZero();
        assertThat(totalCount("appointments", ctx)).isZero();
        assertThat(totalCount("organizations", ctx)).isZero();
        assertThat(totalCount("clinics", ctx)).isZero();
    }

    @Test
    void rule4_noContext_writesAffectZeroRowsOrAreRejected() throws Exception {
        Ctx ctx = Ctx.none();

        assertThat((int) inTx(ctx, c -> update(c, "UPDATE vet.animals SET name = 'x'"))).isZero();
        assertThat((int) inTx(ctx, c -> update(c, "DELETE FROM vet.appointments"))).isZero();
        assertThatThrownBy(() -> inTx(ctx,
                c -> insertAppointment(c, UUID.randomUUID(), ORG_A, BRANCH_A1_S1, PATIENT_A1_S1, "APT-X5")))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void rule4_emptyStringContext_returnsZeroRowsWithoutError() {
        Ctx ctx = Ctx.raw("", "", "", "");

        assertThatCode(() -> {
            assertThat(totalCount("clients", ctx)).isZero();
            assertThat(totalCount("animals", ctx)).isZero();
            assertThat(totalCount("appointments", ctx)).isZero();
            assertThat(totalCount("organizations", ctx)).isZero();
        }).doesNotThrowAnyException();
    }

    @Test
    void rule4_partialContext_organizationOnlyWithoutUserReturnsZeroRows() throws Exception {
        Ctx ctx = Ctx.raw(ORG_A.toString(), null, null, null);

        assertThat(totalCount("animals", ctx)).isZero();
        assertThat(totalCount("appointments", ctx)).isZero();
    }

    @Test
    void rule4_pooledConnectionReuse_doesNotLeakPreviousRequestContext() throws Exception {
        DataSource pool = pooledVetAppDataSource(1);
        try {
            int first = pooledCount(pool, Ctx.tenant(ORG_A, USER_A1_S1, BRANCH_A1_S1));
            int second = pooledCount(pool, Ctx.none());

            assertThat(first).isEqualTo(2);
            assertThat(second).isZero();
        } finally {
            ((com.zaxxer.hikari.HikariDataSource) pool).close();
        }
    }

    // ---------- Kural 5: platform admin yalnızca SELECT ----------

    @Test
    void rule5_platformAdmin_canSelectAllTenants() throws Exception {
        Ctx ctx = Ctx.platform(PLATFORM_ADMIN);

        assertThat(count("organizations", ORG_A, ctx)).isEqualTo(1);
        assertThat(count("organizations", ORG_B, ctx)).isEqualTo(1);
        assertThat(count("animals", PATIENT_A1_S1, ctx)).isEqualTo(1);
        assertThat(count("animals", PATIENT_A2_S1, ctx)).isEqualTo(1);
        assertThat(count("animals", PATIENT_B1_S1, ctx)).isEqualTo(1);
        assertThat(count("appointments", APPT_B1_S1, ctx)).isEqualTo(1);
        assertThat(count("clinics", BRANCH_A2_S1, ctx)).isEqualTo(1);
    }

    @Test
    void rule5_platformAdmin_insertIsRejected() {
        Ctx ctx = Ctx.platform(PLATFORM_ADMIN);

        assertThatThrownBy(() -> inTx(ctx,
                c -> insertAppointment(c, UUID.randomUUID(), ORG_A, BRANCH_A1_S1, PATIENT_A1_S1, "APT-P1")))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> inTx(ctx,
                c -> update(c, "INSERT INTO vet.clients (id, organization_id, display_name) VALUES (?, ?, 'P')", UUID.randomUUID(), ORG_A)))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> inTx(ctx,
                c -> update(c, "INSERT INTO vet.organizations (id, code, name, legal_name, default_currency, timezone_name, is_active) VALUES (?, 9201, 'p', 'p', 'TRY', 'Europe/Istanbul', true)", UUID.randomUUID())))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void rule5_platformAdmin_updateAffectsZeroRows() throws Exception {
        Ctx ctx = Ctx.platform(PLATFORM_ADMIN);

        assertThat((int) inTx(ctx, c -> update(c, "UPDATE vet.animals SET name = 'x' WHERE id = ?", PATIENT_A1_S1))).isZero();
        assertThat((int) inTx(ctx, c -> update(c, "UPDATE vet.organizations SET name = 'x' WHERE id = ?", ORG_A))).isZero();
        assertThat((int) inTx(ctx, c -> update(c, "UPDATE vet.appointments SET reason = 'x' WHERE id = ?", APPT_B1_S1))).isZero();
    }

    @Test
    void rule5_platformAdmin_deleteAffectsZeroRows() throws Exception {
        Ctx ctx = Ctx.platform(PLATFORM_ADMIN);

        assertThat((int) inTx(ctx, c -> update(c, "DELETE FROM vet.appointments WHERE id = ?", APPT_A1_S1))).isZero();
        assertThat((int) inTx(ctx, c -> update(c, "DELETE FROM vet.animals WHERE id = ?", PATIENT_B1_S1))).isZero();
    }

    @Test
    void rule5_unknownOrInactivePlatformAdminId_seesNothing() throws Exception {
        Ctx ctx = Ctx.platform(UUID.randomUUID());

        assertThat(totalCount("animals", ctx)).isZero();
        assertThat(totalCount("organizations", ctx)).isZero();
    }

    // ---------- yardımcılar ----------

    private record Ctx(String organizationId, String userId, String clinicId, String platformAdminId) {
        static Ctx tenant(UUID org, UUID user, UUID clinic) {
            return new Ctx(org.toString(), user.toString(), clinic.toString(), null);
        }

        static Ctx platform(UUID admin) {
            return new Ctx(null, null, null, admin.toString());
        }

        static Ctx none() {
            return new Ctx(null, null, null, null);
        }

        static Ctx raw(String org, String user, String clinic, String platform) {
            return new Ctx(org, user, clinic, platform);
        }
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T run(Connection connection) throws SQLException;
    }

    private static <T> T inTx(Ctx ctx, SqlWork<T> work) throws SQLException {
        try (Connection c = vetAppDataSource().getConnection()) {
            c.setAutoCommit(false);
            try {
                applyContext(c, ctx);
                return work.run(c);
            } finally {
                c.rollback();
            }
        }
    }

    private static void applyContext(Connection c, Ctx ctx) throws SQLException {
        setConfig(c, "app.organization_id", ctx.organizationId());
        setConfig(c, "app.user_id", ctx.userId());
        setConfig(c, "app.clinic_id", ctx.clinicId());
        setConfig(c, "app.platform_admin_id", ctx.platformAdminId());
    }

    private static void setConfig(Connection c, String key, String value) throws SQLException {
        if (value == null) {
            return;
        }
        try (PreparedStatement ps = c.prepareStatement("SELECT set_config(?, ?, true)")) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.execute();
        }
    }

    private static int count(String table, UUID id, Ctx ctx) throws SQLException {
        return inTx(ctx, c -> scalar(c, "SELECT count(*) FROM vet." + table + " WHERE id = ?", id));
    }

    private static int totalCount(String table, Ctx ctx) {
        try {
            return inTx(ctx, c -> scalar(c, "SELECT count(*) FROM vet." + table));
        } catch (SQLException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static int pooledCount(DataSource pool, Ctx ctx) throws SQLException {
        try (Connection c = pool.getConnection()) {
            try {
                applyContext(c, ctx);
                return scalar(c, "SELECT count(*) FROM vet.appointments");
            } finally {
                c.commit();
            }
        }
    }

    private static int scalar(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private static int update(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, params);
            return ps.executeUpdate();
        }
    }

    private static int insertAppointment(Connection c, UUID id, UUID org, UUID branch, UUID patient, String no) throws SQLException {
        return update(c, "INSERT INTO vet.appointments (id, organization_id, clinic_id, client_id, animal_id, appointment_no, starts_at, ends_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, now() + interval '3 day', now() + interval '3 day 1 hour')",
                id, org, branch, patient, patient, no);
    }

    private static void bind(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
    }
}
