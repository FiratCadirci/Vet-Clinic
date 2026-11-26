package org.codeflow.veterineryproject.lab.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "lab_order_items", schema = "vet")
public class LabOrderItems {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "lab_order_id")
    private UUID labOrderId;

    @Column(name = "test_definition_id")
    private UUID testDefinitionId;

    @Column(name = "line_no")
    private Integer lineNo;

    @Column(name = "lab_notes")
    private String labNotes;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public UUID getLabOrderId() { return labOrderId; }
    public void setLabOrderId(UUID labOrderId) { this.labOrderId = labOrderId; }
    public UUID getTestDefinitionId() { return testDefinitionId; }
    public void setTestDefinitionId(UUID testDefinitionId) { this.testDefinitionId = testDefinitionId; }
    public Integer getLineNo() { return lineNo; }
    public void setLineNo(Integer lineNo) { this.lineNo = lineNo; }
    public String getLabNotes() { return labNotes; }
    public void setLabNotes(String labNotes) { this.labNotes = labNotes; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
