package org.codeflow.veterineryproject.lab.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "lab_results", schema = "vet")
public class LabResults {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "lab_order_item_id")
    private UUID labOrderItemId;

    @Column(name = "result_numeric")
    private BigDecimal resultNumeric;

    @Column(name = "result_text")
    private String resultText;

    @Column(name = "result_boolean")
    private Boolean resultBoolean;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_json")
    private String resultJson;

    @Column(name = "unit")
    private String unit;

    @Column(name = "reference_min")
    private BigDecimal referenceMin;

    @Column(name = "reference_max")
    private BigDecimal referenceMax;

    @Column(name = "critical_min")
    private BigDecimal criticalMin;

    @Column(name = "critical_max")
    private BigDecimal criticalMax;

    @Column(name = "measured_at")
    private OffsetDateTime measuredAt;

    @Column(name = "validated_by")
    private UUID validatedBy;

    @Column(name = "validated_at")
    private OffsetDateTime validatedAt;

    @Column(name = "is_current")
    private Boolean isCurrent;

    @Column(name = "comments")
    private String comments;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public UUID getLabOrderItemId() { return labOrderItemId; }
    public void setLabOrderItemId(UUID labOrderItemId) { this.labOrderItemId = labOrderItemId; }
    public BigDecimal getResultNumeric() { return resultNumeric; }
    public void setResultNumeric(BigDecimal resultNumeric) { this.resultNumeric = resultNumeric; }
    public String getResultText() { return resultText; }
    public void setResultText(String resultText) { this.resultText = resultText; }
    public Boolean getResultBoolean() { return resultBoolean; }
    public void setResultBoolean(Boolean resultBoolean) { this.resultBoolean = resultBoolean; }
    public String getResultJson() { return resultJson; }
    public void setResultJson(String resultJson) { this.resultJson = resultJson; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public BigDecimal getReferenceMin() { return referenceMin; }
    public void setReferenceMin(BigDecimal referenceMin) { this.referenceMin = referenceMin; }
    public BigDecimal getReferenceMax() { return referenceMax; }
    public void setReferenceMax(BigDecimal referenceMax) { this.referenceMax = referenceMax; }
    public BigDecimal getCriticalMin() { return criticalMin; }
    public void setCriticalMin(BigDecimal criticalMin) { this.criticalMin = criticalMin; }
    public BigDecimal getCriticalMax() { return criticalMax; }
    public void setCriticalMax(BigDecimal criticalMax) { this.criticalMax = criticalMax; }
    public OffsetDateTime getMeasuredAt() { return measuredAt; }
    public void setMeasuredAt(OffsetDateTime measuredAt) { this.measuredAt = measuredAt; }
    public UUID getValidatedBy() { return validatedBy; }
    public void setValidatedBy(UUID validatedBy) { this.validatedBy = validatedBy; }
    public OffsetDateTime getValidatedAt() { return validatedAt; }
    public void setValidatedAt(OffsetDateTime validatedAt) { this.validatedAt = validatedAt; }
    public Boolean getIsCurrent() { return isCurrent; }
    public void setIsCurrent(Boolean isCurrent) { this.isCurrent = isCurrent; }
    public String getComments() { return comments; }
    public void setComments(String comments) { this.comments = comments; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
