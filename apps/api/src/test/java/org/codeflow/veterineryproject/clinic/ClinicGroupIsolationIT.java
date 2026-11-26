package org.codeflow.veterineryproject.clinic;

import org.codeflow.veterineryproject.common.test.PostgresContainerSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class ClinicGroupIsolationIT extends PostgresContainerSupport {

    private static final UUID ORG = UUID.randomUUID();
    private static final UUID ORG2 = UUID.randomUUID();
    private static final UUID GROUP_A = UUID.randomUUID();
    private static final UUID GROUP_B = UUID.randomUUID();
    private static final UUID CLINIC_A1 = UUID.randomUUID();
    private static final UUID CLINIC_A2 = UUID.randomUUID();
    private static final UUID CLINIC_B1 = UUID.randomUUID();
    private static final UUID CLINIC_ORG2 = UUID.randomUUID();
    private static final UUID USER_A1 = UUID.randomUUID();
    private static final UUID USER_A2 = UUID.randomUUID();
    private static final UUID USER_B1 = UUID.randomUUID();
    private static final UUID USER_ORG2 = UUID.randomUUID();
    private static final UUID ROLE = UUID.randomUUID();
    private static final UUID ROLE2 = UUID.randomUUID();

    @BeforeAll
    static void seed() {
        JdbcTemplate jdbc = new JdbcTemplate(superUserDataSource());
        jdbc.update("INSERT INTO vet.organizations (id, code, name, legal_name, default_currency, timezone_name, is_active) VALUES (?, ?, 'O1', 'O1', 'TRY', 'Europe/Istanbul', true)", ORG, 9101);
        jdbc.update("INSERT INTO vet.organizations (id, code, name, legal_name, default_currency, timezone_name, is_active) VALUES (?, ?, 'O2', 'O2', 'TRY', 'Europe/Istanbul', true)", ORG2, 9102);
        jdbc.update("INSERT INTO vet.clinic_groups (id, organization_id, code, name) VALUES (?, ?, 'GA', 'A')", GROUP_A, ORG);
        jdbc.update("INSERT INTO vet.clinic_groups (id, organization_id, code, name) VALUES (?, ?, 'GB', 'B')", GROUP_B, ORG);
        insertClinic(jdbc, CLINIC_A1, ORG, GROUP_A, "A1");
        insertClinic(jdbc, CLINIC_A2, ORG, GROUP_A, "A2");
        insertClinic(jdbc, CLINIC_B1, ORG, GROUP_B, "B1");
        UUID groupO2 = UUID.randomUUID();
        jdbc.update("INSERT INTO vet.clinic_groups (id, organization_id, code, name) VALUES (?, ?, 'GO', 'O')", groupO2, ORG2);
        insertClinic(jdbc, CLINIC_ORG2, ORG2, groupO2, "X1");
        jdbc.update("INSERT INTO vet.roles (id, organization_id, code, name, is_system_role) VALUES (?, ?, 'admin', 'Admin', true)", ROLE, ORG);
        jdbc.update("INSERT INTO vet.roles (id, organization_id, code, name, is_system_role) VALUES (?, ?, 'admin', 'Admin', true)", ROLE2, ORG2);
        insertUser(jdbc, USER_A1, ORG, CLINIC_A1, ROLE, "a1");
        insertUser(jdbc, USER_A2, ORG, CLINIC_A2, ROLE, "a2");
        insertUser(jdbc, USER_B1, ORG, CLINIC_B1, ROLE, "b1");
        insertUser(jdbc, USER_ORG2, ORG2, CLINIC_ORG2, ROLE2, "o2");
    }

    @Test
    void clinicWithoutGroupShouldBeRejected() {
        JdbcTemplate jdbc = new JdbcTemplate(superUserDataSource());

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO vet.clinics (id, organization_id, code, name) VALUES (?, ?, 'AUTO', 'Auto')", UUID.randomUUID(), ORG))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void clinicCannotUseGroupOfAnotherOrganization() {
        JdbcTemplate jdbc = new JdbcTemplate(superUserDataSource());

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO vet.clinics (id, organization_id, clinic_group_id, code, name) VALUES (?, ?, ?, 'BAD', 'Bad')",
                UUID.randomUUID(), ORG2, GROUP_A))
                .isInstanceOf(DataAccessException.class);
    }
    @Test
    void branchesOfSameGroupShareClientsAndAnimals() throws Exception {
        UUID id = createClientAndAnimalAs(USER_A2, CLINIC_A2, ORG);

        assertThat(count("SELECT count(*) FROM vet.clients WHERE id = ?", USER_A1, ORG, id)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM vet.animals WHERE id = ?", USER_A1, ORG, id)).isEqualTo(1);
    }

    @Test
    void otherGroupAndOtherOrganizationCannotSeeClientsOrAnimals() throws Exception {
        UUID id = createClientAndAnimalAs(USER_A2, CLINIC_A2, ORG);

        assertThat(count("SELECT count(*) FROM vet.clients WHERE id = ?", USER_B1, ORG, id)).isZero();
        assertThat(count("SELECT count(*) FROM vet.animals WHERE id = ?", USER_B1, ORG, id)).isZero();
        assertThat(count("SELECT count(*) FROM vet.clients WHERE id = ?", USER_ORG2, ORG2, id)).isZero();
    }

    private UUID createClientAndAnimalAs(UUID userId, UUID clinicId, UUID orgId) throws Exception {
        UUID id = UUID.randomUUID();
        try (Connection c = vetAppDataSource().getConnection()) {
            c.setAutoCommit(false);
            setContext(c, orgId, userId, clinicId);
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO vet.clients (id, organization_id, display_name) VALUES (?, ?, 'Client')")) {
                ps.setObject(1, id);
                ps.setObject(2, orgId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO vet.animals (id, organization_id, patient_no, name, species_id) SELECT ?, ?, ?, 'Rex', id FROM vet.species LIMIT 1")) {
                ps.setObject(1, id);
                ps.setObject(2, orgId);
                ps.setString(3, "P-" + id.toString().substring(0, 8));
                ps.executeUpdate();
            }
            c.commit();
        }
        return id;
    }

    private int count(String sql, UUID userId, UUID orgId, UUID id) throws Exception {
        try (Connection c = vetAppDataSource().getConnection()) {
            c.setAutoCommit(false);
            setContext(c, orgId, userId, null);
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setObject(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    int value = rs.getInt(1);
                    c.rollback();
                    return value;
                }
            }
        }
    }

    private static void setContext(Connection c, UUID orgId, UUID userId, UUID clinicId) throws Exception {
        try (Statement st = c.createStatement()) {
            st.execute("SELECT set_config('app.organization_id', '" + orgId + "', true), "
                    + "set_config('app.user_id', '" + userId + "', true), "
                    + "set_config('app.clinic_id', '" + (clinicId == null ? "" : clinicId.toString()) + "', true)");
        }
    }

    private static void insertClinic(JdbcTemplate jdbc, UUID id, UUID org, UUID group, String code) {
        jdbc.update("INSERT INTO vet.clinics (id, organization_id, clinic_group_id, code, name) VALUES (?, ?, ?, ?, ?)", id, org, group, code, code);
    }

    private static void insertUser(JdbcTemplate jdbc, UUID userId, UUID org, UUID clinic, UUID role, String email) {
        jdbc.update("INSERT INTO vet.app_users (id, organization_id, email, password_hash, display_name) VALUES (?, ?, ?, 'x', ?)", userId, org, email + "@t.com", email);
        jdbc.update("INSERT INTO vet.user_clinic_roles (organization_id, user_id, clinic_id, role_id, valid_from) VALUES (?, ?, ?, ?, CURRENT_DATE - 1)", org, userId, clinic, role);
    }
}
