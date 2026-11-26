package org.codeflow.veterineryproject.prescription.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "prescriptions", schema = "vet")
public class Prescriptions {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "encounter_id")
    private UUID encounterId;

    @Column(name = "animal_id")
    private UUID animalId;

    @Column(name = "prescribed_by")
    private UUID prescribedBy;

    @Column(name = "prescription_no")
    private String prescriptionNo;

    @Column(name = "prescribed_at")
    private OffsetDateTime prescribedAt;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Column(name = "general_instructions")
    private String generalInstructions;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public UUID getEncounterId() { return encounterId; }
    public void setEncounterId(UUID encounterId) { this.encounterId = encounterId; }
    public UUID getAnimalId() { return animalId; }
    public void setAnimalId(UUID animalId) { this.animalId = animalId; }
    public UUID getPrescribedBy() { return prescribedBy; }
    public void setPrescribedBy(UUID prescribedBy) { this.prescribedBy = prescribedBy; }
    public String getPrescriptionNo() { return prescriptionNo; }
    public void setPrescriptionNo(String prescriptionNo) { this.prescriptionNo = prescriptionNo; }
    public OffsetDateTime getPrescribedAt() { return prescribedAt; }
    public void setPrescribedAt(OffsetDateTime prescribedAt) { this.prescribedAt = prescribedAt; }
    public LocalDate getValidUntil() { return validUntil; }
    public void setValidUntil(LocalDate validUntil) { this.validUntil = validUntil; }
    public String getGeneralInstructions() { return generalInstructions; }
    public void setGeneralInstructions(String generalInstructions) { this.generalInstructions = generalInstructions; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
