package org.codeflow.veterineryproject.lab.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "lab_test_definitions", schema = "vet")
public class LabTestDefinitions {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "code")
    private String code;

    @Column(name = "name")
    private String name;

    @Column(name = "category")
    private String category;

    @Column(name = "specimen_type")
    private String specimenType;

    @Column(name = "method")
    private String method;

    @Column(name = "default_unit")
    private String defaultUnit;

    @Column(name = "turnaround_hours")
    private BigDecimal turnaroundHours;

    @Column(name = "requires_fasting")
    private Boolean requiresFasting;

    @Column(name = "instructions")
    private String instructions;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getSpecimenType() { return specimenType; }
    public void setSpecimenType(String specimenType) { this.specimenType = specimenType; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public String getDefaultUnit() { return defaultUnit; }
    public void setDefaultUnit(String defaultUnit) { this.defaultUnit = defaultUnit; }
    public BigDecimal getTurnaroundHours() { return turnaroundHours; }
    public void setTurnaroundHours(BigDecimal turnaroundHours) { this.turnaroundHours = turnaroundHours; }
    public Boolean getRequiresFasting() { return requiresFasting; }
    public void setRequiresFasting(Boolean requiresFasting) { this.requiresFasting = requiresFasting; }
    public String getInstructions() { return instructions; }
    public void setInstructions(String instructions) { this.instructions = instructions; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
