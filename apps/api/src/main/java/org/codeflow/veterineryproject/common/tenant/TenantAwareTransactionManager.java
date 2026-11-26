package org.codeflow.veterineryproject.common.tenant;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

public class TenantAwareTransactionManager extends JpaTransactionManager {

    public TenantAwareTransactionManager(EntityManagerFactory entityManagerFactory) {
        super(entityManagerFactory);
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) throws TransactionException {
        super.doBegin(transaction, definition);
        applyTenantContext();
    }

    private void applyTenantContext() {
        TenantContext.ContextValues contextValues = TenantContext.get().orElse(null);
        if (contextValues == null) {
            return;
        }

        DataSource dataSource = getDataSource();
        if (dataSource == null) {
            throw new IllegalStateException("DataSource is not configured for TenantAwareTransactionManager.");
        }

        Connection connection = DataSourceUtils.getConnection(dataSource);
        setIfPresent(connection, "app.organization_id", contextValues.organizationId());
        setIfPresent(connection, "app.user_id", contextValues.userId());
        setIfPresent(connection, "app.clinic_id", contextValues.clinicId());
        setIfPresent(connection, "app.platform_admin_id", contextValues.platformAdminId());
    }

    private void setIfPresent(Connection connection, String key, UUID value) {
        if (value == null) {
            return;
        }

        String sql = "select set_config('" + key + "', ?, true)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value.toString());
            statement.execute();
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to set tenant context key: " + key, ex);
        }
    }
}

