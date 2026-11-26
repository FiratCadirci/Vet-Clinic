package org.codeflow.veterineryproject.auth.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "platform_admin_access_log", schema = "vet")
public class PlatformAdminAccessLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "platform_admin_id")
    private UUID platformAdminId;

    @Column(name = "operation")
    private String operation;

    @Column(name = "table_name")
    private String tableName;

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "row_pk")
    private String rowPk;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "old_row")
    private String oldRow;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_row")
    private String newRow;

    @Column(name = "occurred_at")
    private OffsetDateTime occurredAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getPlatformAdminId() { return platformAdminId; }
    public void setPlatformAdminId(UUID platformAdminId) { this.platformAdminId = platformAdminId; }
    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }
    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }
    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public String getRowPk() { return rowPk; }
    public void setRowPk(String rowPk) { this.rowPk = rowPk; }
    public String getOldRow() { return oldRow; }
    public void setOldRow(String oldRow) { this.oldRow = oldRow; }
    public String getNewRow() { return newRow; }
    public void setNewRow(String newRow) { this.newRow = newRow; }
    public OffsetDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(OffsetDateTime occurredAt) { this.occurredAt = occurredAt; }
}
