package org.codeflow.veterineryproject.lab.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "lab_reference_ranges", schema = "vet")
public class LabReferenceRanges {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "test_definition_id")
    private UUID testDefinitionId;

    @Column(name = "species_id")
    private UUID speciesId;

    @Column(name = "sex")
    private String sex;

    @Column(name = "age_days_min")
    private Integer ageDaysMin;

    @Column(name = "age_days_max")
    private Integer ageDaysMax;

    @Column(name = "reference_min")
    private BigDecimal referenceMin;

    @Column(name = "reference_max")
    private BigDecimal referenceMax;

    @Column(name = "critical_min")
    private BigDecimal criticalMin;

    @Column(name = "critical_max")
    private BigDecimal criticalMax;

    @Column(name = "unit")
    private String unit;

    @Column(name = "source_note")
    private String sourceNote;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public UUID getTestDefinitionId() { return testDefinitionId; }
    public void setTestDefinitionId(UUID testDefinitionId) { this.testDefinitionId = testDefinitionId; }
    public UUID getSpeciesId() { return speciesId; }
    public void setSpeciesId(UUID speciesId) { this.speciesId = speciesId; }
    public String getSex() { return sex; }
    public void setSex(String sex) { this.sex = sex; }
    public Integer getAgeDaysMin() { return ageDaysMin; }
    public void setAgeDaysMin(Integer ageDaysMin) { this.ageDaysMin = ageDaysMin; }
    public Integer getAgeDaysMax() { return ageDaysMax; }
    public void setAgeDaysMax(Integer ageDaysMax) { this.ageDaysMax = ageDaysMax; }
    public BigDecimal getReferenceMin() { return referenceMin; }
    public void setReferenceMin(BigDecimal referenceMin) { this.referenceMin = referenceMin; }
    public BigDecimal getReferenceMax() { return referenceMax; }
    public void setReferenceMax(BigDecimal referenceMax) { this.referenceMax = referenceMax; }
    public BigDecimal getCriticalMin() { return criticalMin; }
    public void setCriticalMin(BigDecimal criticalMin) { this.criticalMin = criticalMin; }
    public BigDecimal getCriticalMax() { return criticalMax; }
    public void setCriticalMax(BigDecimal criticalMax) { this.criticalMax = criticalMax; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public String getSourceNote() { return sourceNote; }
    public void setSourceNote(String sourceNote) { this.sourceNote = sourceNote; }
}
