package org.codeflow.veterineryproject.common.test;

import org.codeflow.veterineryproject.staff.service.InvitationToken;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Klinik kaydı / personel daveti / şube testlerinin verisi. Organizasyonlar API ile (signup) açılır; adları
 * {@link #ORG_PREFIX} ile başlar, paket kodları {@link #PLAN_PREFIX} ile. Sınıf sonunda {@link #cleanup} hepsini siler,
 * böylece TenantTestData / RegistrationTestData satır sayıları etkilenmez.
 */
public final class OrgTestData {

    public static final String ORG_PREFIX = "ITORG ";
    public static final String PLAN_PREFIX = "it-org-";
    public static final String PASSWORD = "Guclu-Parola-2026";

    private OrgTestData() {
    }

    /** Testlere özel paket (aylık); kod tekil olsun diye rastgele son ek alır. */
    public static String createPlan(DataSource superUser, int maxBranches, int maxUsers) {
        String code = PLAN_PREFIX + UUID.randomUUID().toString().substring(0, 8);
        exec(superUser, "INSERT INTO vet.subscription_plans (code, name, billing_period, price, max_branches, max_users) "
                + "VALUES (?, ?, 'MONTHLY', 100, ?, ?)", code, "IT Paket " + code, maxBranches, maxUsers);
        return code;
    }

    public static UUID roleId(DataSource superUser, UUID organizationId, String roleCode) {
        return queryUuid(superUser, "SELECT id FROM vet.roles WHERE organization_id = ? AND code = ?", organizationId, roleCode);
    }

    public static int organizationCode(DataSource superUser, UUID organizationId) {
        return queryInt(superUser, "SELECT code FROM vet.organizations WHERE id = ?", organizationId);
    }

    /** Davet kaydını bilinen bir token ile doğrudan yazar (API'nin token'ı yalnızca olayla verdiği durumlar için). */
    public static String insertInvitation(DataSource superUser, UUID organizationId, String email, String roleCode,
                                          UUID invitedBy, Collection<UUID> clinicIds) {
        InvitationToken token = InvitationToken.generate();
        String array = clinicIds.stream().map(UUID::toString).collect(Collectors.joining(",", "{", "}"));
        exec(superUser, "INSERT INTO vet.staff_invitations (organization_id, email, role_id, clinic_ids, token_hash, invited_by, expires_at) "
                        + "VALUES (?, ?, ?, ?::uuid[], ?, ?, now() + interval '7 days')",
                organizationId, email, roleId(superUser, organizationId, roleCode), array, token.hash(), invitedBy);
        return token.raw();
    }

    public static void expireInvitation(DataSource superUser, String rawToken) {
        exec(superUser, "UPDATE vet.staff_invitations SET created_at = now() - interval '8 days', expires_at = now() - interval '1 day' "
                + "WHERE token_hash = ?", new InvitationToken(rawToken).hash());
    }

    public static long count(DataSource superUser, String sql, Object... params) {
        return queryInt(superUser, sql, params);
    }

    public static UUID queryUuid(DataSource superUser, String sql, Object... params) {
        try (Connection c = superUser.getConnection(); PreparedStatement ps = prepare(c, sql, params); ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getObject(1, UUID.class) : null;
        } catch (SQLException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public static int queryInt(DataSource superUser, String sql, Object... params) {
        try (Connection c = superUser.getConnection(); PreparedStatement ps = prepare(c, sql, params); ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        } catch (SQLException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public static void exec(DataSource superUser, String sql, Object... params) {
        try (Connection c = superUser.getConnection(); PreparedStatement ps = prepare(c, sql, params)) {
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** Test organizasyonlarının tüm satırlarını siler (trigger/FK'ler replica modunda devre dışı). */
    public static synchronized void cleanup(DataSource superUser) {
        try (Connection c = superUser.getConnection()) {
            c.setAutoCommit(false);
            List<String> tables = new ArrayList<>();
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("""
                         SELECT c.table_name FROM information_schema.columns c
                         JOIN information_schema.tables t ON t.table_schema = c.table_schema AND t.table_name = c.table_name
                         WHERE c.table_schema = 'vet' AND t.table_type = 'BASE TABLE' AND c.column_name = 'organization_id'
                         """)) {
                while (rs.next()) {
                    tables.add(rs.getString(1));
                }
            }
            try (Statement st = c.createStatement()) {
                st.execute("SET LOCAL session_replication_role = replica");
                String orgs = "(SELECT id FROM vet.organizations WHERE name LIKE '" + ORG_PREFIX + "%')";
                for (String table : tables) {
                    st.execute("DELETE FROM vet." + table + " WHERE organization_id IN " + orgs);
                }
                st.execute("DELETE FROM vet.organizations WHERE name LIKE '" + ORG_PREFIX + "%'");
                st.execute("DELETE FROM vet.subscription_plans WHERE code LIKE '" + PLAN_PREFIX + "%'");
            }
            c.commit();
        } catch (SQLException ex) {
            throw new IllegalStateException("Organizasyon test verisi temizlenemedi: " + ex.getMessage(), ex);
        }
    }

    private static PreparedStatement prepare(Connection c, String sql, Object... params) throws SQLException {
        PreparedStatement ps = c.prepareStatement(sql);
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
        return ps;
    }
}
