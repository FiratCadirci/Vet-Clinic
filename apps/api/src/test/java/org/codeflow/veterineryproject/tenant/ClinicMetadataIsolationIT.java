package org.codeflow.veterineryproject.tenant;

import org.codeflow.veterineryproject.common.test.PostgresContainerSupport;
import org.codeflow.veterineryproject.common.test.TenantTestData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.codeflow.veterineryproject.common.test.TenantTestData.*;

/** clinic_groups (klinik) ve clinics (şube) meta verisinin RLS izolasyonu; vet_app rolüyle çalışır. */
class ClinicMetadataIsolationIT extends PostgresContainerSupport {

    @BeforeAll
    static void seed() {
        TenantTestData.seed(superUserDataSource());
    }

    @Test
    void metaRule2_clinicGroups_userCannotSeeOtherClinicGroupInSameOrganization() throws Exception {
        assertThat(ids("clinic_groups", USER_A1_S1, ORG_A)).containsExactly(CLINIC_A1);
        assertThat(count("clinic_groups", CLINIC_A2, ORG_A, USER_A1_S1)).isZero();
    }

    @Test
    void metaRule2_clinics_userCannotSeeBranchesOfOtherClinicInSameOrganization() throws Exception {
        assertThat(count("clinics", BRANCH_A2_S1, ORG_A, USER_A1_S1)).isZero();
        assertThat(ids("clinics", USER_A1_S1, ORG_A)).doesNotContain(BRANCH_A2_S1);
        assertThat(ids("clinics", USER_A2_S1, ORG_A)).containsExactly(BRANCH_A2_S1);
    }

    @Test
    void metaRule1_clinics_userSeesAllBranchesOfOwnClinic() throws Exception {
        assertThat(ids("clinics", USER_A1_S1, ORG_A)).containsExactlyInAnyOrder(BRANCH_A1_S1, BRANCH_A1_S2);
        assertThat(ids("clinics", USER_A1_S2, ORG_A)).containsExactlyInAnyOrder(BRANCH_A1_S1, BRANCH_A1_S2);
    }

    @Test
    void metaRule3_otherOrganizationUserSeesNoClinicOrClinicGroupOfOrgA() throws Exception {
        assertThat(ids("clinic_groups", USER_B1_S1, ORG_B)).containsExactly(CLINIC_B1);
        assertThat(ids("clinics", USER_B1_S1, ORG_B)).containsExactly(BRANCH_B1_S1);
        assertThat(count("clinic_groups", CLINIC_A1, ORG_B, USER_B1_S1)).isZero();
        assertThat(count("clinics", BRANCH_A1_S1, ORG_B, USER_B1_S1)).isZero();
    }

    @Test
    void metaRule3_forgedOrganizationContextDoesNotExposeOtherOrganization() throws Exception {
        assertThat(ids("clinics", USER_B1_S1, ORG_A)).isEmpty();
        assertThat(ids("clinic_groups", USER_B1_S1, ORG_A)).isEmpty();
    }

    @Test
    void metaRule4_noContext_clinicTablesReturnZeroRows() throws Exception {
        assertThat(idsRaw("clinic_groups", null, null)).isEmpty();
        assertThat(idsRaw("clinics", null, null)).isEmpty();
    }

    @Test
    void metaRule4_emptyStringContext_clinicTablesReturnZeroRowsWithoutError() throws Exception {
        assertThat(idsRaw("clinic_groups", "", "")).isEmpty();
        assertThat(idsRaw("clinics", "", "")).isEmpty();
    }

    @Test
    void metaRule4_organizationWithoutUser_clinicTablesReturnZeroRows() throws Exception {
        assertThat(idsRaw("clinics", null, ORG_A.toString())).isEmpty();
        assertThat(idsRaw("clinic_groups", null, ORG_A.toString())).isEmpty();
    }

    @Test
    void metaRule5_platformAdminCanSelectAllClinicsAndGroups() throws Exception {
        assertThat(platformIds("clinic_groups")).contains(CLINIC_A1, CLINIC_A2, CLINIC_B1);
        assertThat(platformIds("clinics")).contains(BRANCH_A1_S1, BRANCH_A1_S2, BRANCH_A2_S1, BRANCH_B1_S1);
    }

    @Test
    void metaRule5_platformAdminCannotWriteClinicsOrGroups() throws Exception {
        assertThat(inPlatformTx("UPDATE vet.clinics SET name = 'x' WHERE id = ?", BRANCH_A1_S1)).isZero();
        assertThat(inPlatformTx("DELETE FROM vet.clinic_groups WHERE id = ?", CLINIC_A1)).isZero();
        assertThatThrownBy(() -> inPlatformTx(
                "INSERT INTO vet.clinic_groups (id, organization_id, code, name) VALUES (?, '" + ORG_A + "', 'PX', 'x')",
                UUID.randomUUID())).isInstanceOf(SQLException.class);
    }

    @Test
    void metaWrite_updateAndDeleteOfOtherClinicRowsAffectZeroRows() throws Exception {
        assertThat(update(USER_A1_S1, ORG_A, "UPDATE vet.clinics SET name = 'x' WHERE id = ?", BRANCH_A2_S1)).isZero();
        assertThat(update(USER_A1_S1, ORG_A, "UPDATE vet.clinic_groups SET name = 'x' WHERE id = ?", CLINIC_A2)).isZero();
        assertThat(update(USER_A1_S1, ORG_A, "DELETE FROM vet.clinics WHERE id = ?", BRANCH_A2_S1)).isZero();
        assertThat(update(USER_A1_S1, ORG_A, "DELETE FROM vet.clinic_groups WHERE id = ?", CLINIC_A2)).isZero();
    }

    @Test
    void metaWrite_cannotMoveBranchIntoOtherClinicGroup() {
        assertThatThrownBy(() -> update(USER_A1_S1, ORG_A,
                "UPDATE vet.clinics SET clinic_group_id = '" + CLINIC_A2 + "' WHERE id = ?", BRANCH_A1_S1))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void metaWrite_cannotCreateBranchInOtherClinicGroup() {
        assertThatThrownBy(() -> update(USER_A1_S1, ORG_A, insertBranchSql(CLINIC_A2), UUID.randomUUID()))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void metaWrite_canCreateBranchInOwnClinicGroup() throws Exception {
        assertThat(update(USER_A1_S1, ORG_A, insertBranchSql(CLINIC_A1), UUID.randomUUID())).isEqualTo(1);
    }

    @Test
    void metaWrite_applicationRoleCannotCreateClinicGroup() {
        assertThatThrownBy(() -> update(USER_A1_S1, ORG_A,
                "INSERT INTO vet.clinic_groups (id, organization_id, code, name) VALUES (?, '" + ORG_A + "', 'NEW', 'New')",
                UUID.randomUUID())).isInstanceOf(SQLException.class);
    }

    @Test
    void metaSecurity_securityDefinerFunctionsAreOwnedByRoleThatBypassesRls() throws Exception {
        try (Connection c = superUserDataSource().getConnection();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT p.proname, r.rolsuper OR r.rolbypassrls
                     FROM pg_proc p
                     JOIN pg_namespace n ON n.oid = p.pronamespace AND n.nspname = 'vet'
                     JOIN pg_roles r ON r.oid = p.proowner
                     WHERE p.prosecdef
                       AND p.proname IN ('current_user_in_clinic_group', 'current_user_has_clinic_group_access', 'clinics_in_same_group')
                     """);
             ResultSet rs = ps.executeQuery()) {
            int rows = 0;
            while (rs.next()) {
                rows++;
                assertThat(rs.getBoolean(2)).as(rs.getString(1)).isTrue();
            }
            assertThat(rows).isEqualTo(3);
        }
    }

    @Test
    void userRule2_appUsers_userSeesOnlyOwnClinicUsers() throws Exception {
        assertThat(ids("app_users", USER_A1_S1, ORG_A)).containsExactlyInAnyOrder(USER_A1_S1, USER_A1_S2);
        assertThat(ids("app_users", USER_A2_S1, ORG_A)).containsExactly(USER_A2_S1);
        assertThat(ids("app_users", USER_B1_S1, ORG_B)).containsExactly(USER_B1_S1);
        assertThat(ids("app_users", USER_B1_S1, ORG_A)).isEmpty();
    }

    @Test
    void userRule2_userClinicRoles_userSeesOnlyOwnClinicAssignments() throws Exception {
        assertThat(count("user_clinic_roles", ORG_A, USER_A1_S1, "clinic_id = '" + BRANCH_A2_S1 + "'")).isZero();
        assertThat(count("user_clinic_roles", ORG_A, USER_A1_S1, "clinic_id IN ('" + BRANCH_A1_S1 + "', '" + BRANCH_A1_S2 + "')")).isEqualTo(2);
        assertThat(count("user_clinic_roles", ORG_A, USER_A1_S1, "TRUE")).isEqualTo(2);
    }

    @Test
    void userRule4_noContext_usersAndRolesReturnZeroRows() throws Exception {
        assertThat(idsRaw("app_users", null, null)).isEmpty();
        assertThat(idsRaw("user_clinic_roles", "", "")).isEmpty();
    }

    @Test
    void userWrite_cannotUpdateDeleteOrGrantRoleOutsideOwnClinic() {
        assertThatThrownBy(() -> update(USER_A1_S1, ORG_A,
                "INSERT INTO vet.user_clinic_roles (id, organization_id, user_id, clinic_id, role_id) SELECT ?, '" + ORG_A + "', '"
                        + USER_A1_S1 + "', '" + BRANCH_A2_S1 + "', role_id FROM vet.user_clinic_roles WHERE user_id = '" + USER_A1_S1 + "'",
                UUID.randomUUID())).isInstanceOf(SQLException.class);
    }

    @Test
    void userWrite_updateAndDeleteOfOtherClinicUserAffectZeroRows() throws Exception {
        assertThat(update(USER_A1_S1, ORG_A, "UPDATE vet.app_users SET display_name = 'x' WHERE id = ?", USER_A2_S1)).isZero();
        assertThat(update(USER_A1_S1, ORG_A, "DELETE FROM vet.app_users WHERE id = ?", USER_A2_S1)).isZero();
        assertThat(update(USER_A1_S1, ORG_A, "DELETE FROM vet.user_clinic_roles WHERE user_id = ?", USER_A2_S1)).isZero();
    }

    @Test
    void userFlow_preAuthLoginLookupResolvesIdWithoutExposingOtherRows() throws Exception {
        assertThat(query("SELECT vet.resolve_login_user_id('" + ORG_A + "', 'A1S1@test.com')", ORG_A.toString(), null, null))
                .containsExactly(USER_A1_S1);
        assertThat(query("SELECT vet.resolve_login_user_id('" + ORG_B + "', '" + EMAIL_A1_S1 + "')", ORG_B.toString(), null, null))
                .containsExactly((UUID) null);
    }

    private static int count(String table, UUID org, UUID user, String where) throws SQLException {
        return query("SELECT id FROM vet." + table + " WHERE " + where, org.toString(), user.toString(), null).size();
    }

    private static String insertBranchSql(UUID groupId) {
        return "INSERT INTO vet.clinics (id, organization_id, clinic_group_id, code, name) VALUES (?, '" + ORG_A
                + "', '" + groupId + "', 'N" + System.nanoTime() + "', 'Yeni')";
    }

    private static List<UUID> ids(String table, UUID user, UUID org) throws SQLException {
        return idsRaw(table, user.toString(), org.toString());
    }

    private static List<UUID> idsRaw(String table, String user, String org) throws SQLException {
        return query("SELECT id FROM vet." + table, org, user, null);
    }

    private static int count(String table, UUID id, UUID org, UUID user) throws SQLException {
        return query("SELECT id FROM vet." + table + " WHERE id = '" + id + "'", org.toString(), user.toString(), null).size();
    }

    private static List<UUID> platformIds(String table) throws SQLException {
        return query("SELECT id FROM vet." + table, null, null, PLATFORM_ADMIN.toString());
    }

    private static List<UUID> query(String sql, String org, String user, String platform) throws SQLException {
        try (Connection c = vetAppDataSource().getConnection()) {
            c.setAutoCommit(false);
            try {
                context(c, org, user, platform);
                List<UUID> result = new ArrayList<>();
                try (PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        result.add(rs.getObject(1, UUID.class));
                    }
                }
                return result;
            } finally {
                c.rollback();
            }
        }
    }

    private static int update(UUID user, UUID org, String sql, UUID id) throws SQLException {
        return run(user.toString(), org.toString(), null, sql, id);
    }

    private static int inPlatformTx(String sql, UUID id) throws SQLException {
        return run(null, null, PLATFORM_ADMIN.toString(), sql, id);
    }

    private static int run(String user, String org, String platform, String sql, UUID id) throws SQLException {
        try (Connection c = vetAppDataSource().getConnection()) {
            c.setAutoCommit(false);
            try {
                context(c, org, user, platform);
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    if (sql.contains("?")) {
                        ps.setObject(1, id);
                    }
                    return ps.executeUpdate();
                }
            } finally {
                c.rollback();
            }
        }
    }

    private static void context(Connection c, String org, String user, String platform) throws SQLException {
        set(c, "app.organization_id", org);
        set(c, "app.user_id", user);
        set(c, "app.platform_admin_id", platform);
    }

    private static void set(Connection c, String key, String value) throws SQLException {
        if (value == null) {
            return;
        }
        try (PreparedStatement ps = c.prepareStatement("SELECT set_config(?, ?, true)")) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.execute();
        }
    }
}
