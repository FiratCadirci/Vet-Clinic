package org.codeflow.veterineryproject.common.tenant;

import org.codeflow.veterineryproject.common.test.PostgresContainerSupport;
import org.codeflow.veterineryproject.organization.model.Organization;
import org.codeflow.veterineryproject.organization.repository.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class TenantAwareTransactionManagerIT extends PostgresContainerSupport {

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private OrganizationRepository organizationRepository;

    private JdbcTemplate superUserJdbcTemplate;

    @BeforeEach
    void setUp() {
        DataSource superUserDataSource = superUserDataSource();
        this.superUserJdbcTemplate = new JdbcTemplate(superUserDataSource);
        TenantContext.clear();
    }

    @Test
    void shouldApplyOrganizationRlsByTransactionContext() {
        assertThat(transactionManager).isInstanceOf(TenantAwareTransactionManager.class);

        UUID organizationOne = UUID.randomUUID();
        UUID organizationTwo = UUID.randomUUID();

        insertOrganization(organizationOne, 9301, "RLS Org One");
        insertOrganization(organizationTwo, 9302, "RLS Org Two");

        List<Organization> withoutTenant = readOrganizationsInTransaction();
        assertThat(withoutTenant).isEmpty();

        try (TenantContext.Scope ignored = TenantContext.withTenant(organizationOne, UUID.randomUUID(), null)) {
            List<Organization> scopedResult = readOrganizationsInTransaction();
            assertThat(scopedResult).hasSize(1);
            assertThat(scopedResult.get(0).getId()).isEqualTo(organizationOne);
        }
    }

    private void insertOrganization(UUID id, int code, String name) {
        superUserJdbcTemplate.update(
                """
                        INSERT INTO vet.organizations (id, code, name, default_currency, timezone_name, is_active)
                        VALUES (?, ?, ?, 'TRY', 'Europe/Istanbul', true)
                        """,
                id, code, name
        );
    }

    private List<Organization> readOrganizationsInTransaction() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setReadOnly(true);
        return transactionTemplate.execute(status -> organizationRepository.findAll());
    }
}

