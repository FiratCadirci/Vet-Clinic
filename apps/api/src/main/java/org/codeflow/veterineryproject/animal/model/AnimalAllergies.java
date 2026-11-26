package org.codeflow.veterineryproject.animal.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "animal_allergies", schema = "vet")
public class AnimalAllergies {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "animal_id")
    private UUID animalId;

    @Column(name = "substance")
    private String substance;

    @Column(name = "reaction")
    private String reaction;

    @Column(name = "severity")
    private String severity;

    @Column(name = "onset_date")
    private LocalDate onsetDate;

    @Column(name = "resolved_at")
    private LocalDate resolvedAt;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "recorded_by")
    private UUID recordedBy;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public UUID getAnimalId() { return animalId; }
    public void setAnimalId(UUID animalId) { this.animalId = animalId; }
    public String getSubstance() { return substance; }
    public void setSubstance(String substance) { this.substance = substance; }
    public String getReaction() { return reaction; }
    public void setReaction(String reaction) { this.reaction = reaction; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public LocalDate getOnsetDate() { return onsetDate; }
    public void setOnsetDate(LocalDate onsetDate) { this.onsetDate = onsetDate; }
    public LocalDate getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDate resolvedAt) { this.resolvedAt = resolvedAt; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    public UUID getRecordedBy() { return recordedBy; }
    public void setRecordedBy(UUID recordedBy) { this.recordedBy = recordedBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
