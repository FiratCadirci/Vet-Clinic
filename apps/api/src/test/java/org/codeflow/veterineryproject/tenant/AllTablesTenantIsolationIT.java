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
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.codeflow.veterineryproject.common.test.TenantTestData.*;

/**
 * information_schema üzerinden vet şemasındaki tüm tabloları dolaşır.
 * Yeni bir tablo eklenip RLS/policy unutulursa bu testler kırılır.
 */
class AllTablesTenantIsolationIT extends PostgresContainerSupport {

    /** Tenant verisi taşımayan, bilinçli olarak RLS dışı bırakılmış referans/platform tabloları. */
    private static final Set<String> NON_TENANT_TABLES = Set.of(
            "species", "breeds", "permissions", "subscription_plans", "commission_rules",
            "platform_admins", "platform_refresh_tokens", "platform_admin_access_log", "flyway_schema_history");

    @BeforeAll
    static void seed() {
        TenantTestData.seed(superUserDataSource());
    }

    @Test
    void everyTableIsEitherTenantTableWithForcedRlsAndPolicyOrExplicitlyListedAsNonTenant() throws Exception {
        List<String> problems = new ArrayList<>();
        for (String table : allTables()) {
            if (NON_TENANT_TABLES.contains(table)) {
                continue;
            }
            if (!rlsEnabledAndForced(table)) {
                problems.add(table + ": RLS aktif/forced değil");
            } else if (policyCount(table) == 0) {
                problems.add(table + ": policy yok (RLS açık ama hiçbir satır dönmez/yazılamaz)");
            }
        }
        assertThat(problems).as("RLS'i unutulmuş tablolar").isEmpty();
    }

    @Test
    void everyTenantTableWithOrganizationIdIsCoveredByRls() throws Exception {
        List<String> uncovered = new ArrayList<>();
        for (String table : tablesWithColumn("organization_id")) {
            if (!NON_TENANT_TABLES.contains(table) && !rlsEnabledAndForced(table)) {
                uncovered.add(table);
            }
        }
        assertThat(uncovered).as("organization_id taşıyan ama RLS'siz tablolar").isEmpty();
    }

    @Test
    void noContext_everyRlsTableReturnsZeroRowsWithoutError() throws Exception {
        List<String> leaking = new ArrayList<>();
        for (String table : rlsTables()) {
            if (visibleRows(table, null, null, "TRUE") != 0) {
                leaking.add(table);
            }
        }
        assertThat(leaking).as("context yokken satır döndüren tablolar").isEmpty();
    }

    @Test
    void emptyStringContext_everyRlsTableReturnsZeroRowsWithoutError() throws Exception {
        List<String> leaking = new ArrayList<>();
        for (String table : rlsTables()) {
            if (visibleRows(table, "", "", "TRUE") != 0) {
                leaking.add(table);
            }
        }
        assertThat(leaking).as("boş string context ile satır döndüren tablolar").isEmpty();
    }

    @Test
    void otherOrganizationRowsAreInvisible_inEveryTableWithOrganizationId() throws Exception {
        List<String> leaking = new ArrayList<>();
        for (String table : tablesWithColumn("organization_id")) {
            String foreign = "organization_id <> '" + ORG_A + "'";
            if (visibleRows(table, ORG_A.toString(), USER_A1_S1.toString(), foreign) != 0) {
                leaking.add(table);
            }
        }
        assertThat(leaking).as("başka organizasyon satırı sızdıran tablolar").isEmpty();
    }

    @Test
    void otherClinicRowsAreInvisible_inEveryTableWithClinicId() throws Exception {
        List<String> leaking = new ArrayList<>();
        String foreign = "clinic_id IN ('" + BRANCH_A2_S1 + "', '" + BRANCH_B1_S1 + "')";
        for (String table : tablesWithColumn("clinic_id")) {
            if (visibleRows(table, ORG_A.toString(), USER_A1_S1.toString(), foreign) != 0) {
                leaking.add(table);
            }
        }
        assertThat(leaking).as("başka kliniğin satırını sızdıran tablolar").isEmpty();
    }

    private static List<String> allTables() throws SQLException {
        return strings("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'vet' AND table_type = 'BASE TABLE' ORDER BY 1
                """);
    }

    private static List<String> tablesWithColumn(String column) throws SQLException {
        return strings("""
                SELECT DISTINCT c.table_name FROM information_schema.columns c
                JOIN information_schema.tables t ON t.table_schema = c.table_schema AND t.table_name = c.table_name
                WHERE c.table_schema = 'vet' AND t.table_type = 'BASE TABLE' AND c.column_name = '%s' ORDER BY 1
                """.formatted(column));
    }

    private static List<String> rlsTables() throws SQLException {
        Set<String> result = new TreeSet<>();
        for (String table : allTables()) {
            if (rlsEnabledAndForced(table)) {
                result.add(table);
            }
        }
        return new ArrayList<>(result);
    }

    private static boolean rlsEnabledAndForced(String table) throws SQLException {
        return !strings("SELECT relname FROM pg_class WHERE oid = 'vet.\"" + table
                + "\"'::regclass AND relrowsecurity AND relforcerowsecurity").isEmpty();
    }

    private static int policyCount(String table) throws SQLException {
        return strings("SELECT policyname FROM pg_policies WHERE schemaname = 'vet' AND tablename = '" + table + "'").size();
    }

    private static List<String> strings(String sql) throws SQLException {
        List<String> result = new ArrayList<>();
        try (Connection c = superUserDataSource().getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(rs.getString(1));
            }
        }
        return result;
    }

    private static int visibleRows(String table, String org, String user, String where) throws SQLException {
        try (Connection c = vetAppDataSource().getConnection()) {
            c.setAutoCommit(false);
            try {
                set(c, "app.organization_id", org);
                set(c, "app.user_id", user);
                try (PreparedStatement ps = c.prepareStatement("SELECT count(*) FROM vet.\"" + table + "\" WHERE " + where);
                     ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getInt(1);
                }
            } finally {
                c.rollback();
            }
        }
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
