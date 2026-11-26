package org.codeflow.veterineryproject.lab.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "lab_orders", schema = "vet")
public class LabOrders {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "clinic_id")
    private UUID clinicId;

    @Column(name = "animal_id")
    private UUID animalId;

    @Column(name = "encounter_id")
    private UUID encounterId;

    @Column(name = "ordered_by_vet_id")
    private UUID orderedByVetId;

    @Column(name = "order_no")
    private String orderNo;

    @Column(name = "ordered_at")
    private OffsetDateTime orderedAt;

    @Column(name = "fasting_confirmed")
    private Boolean fastingConfirmed;

    @Column(name = "clinical_notes")
    private String clinicalNotes;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public UUID getClinicId() { return clinicId; }
    public void setClinicId(UUID clinicId) { this.clinicId = clinicId; }
    public UUID getAnimalId() { return animalId; }
    public void setAnimalId(UUID animalId) { this.animalId = animalId; }
    public UUID getEncounterId() { return encounterId; }
    public void setEncounterId(UUID encounterId) { this.encounterId = encounterId; }
    public UUID getOrderedByVetId() { return orderedByVetId; }
    public void setOrderedByVetId(UUID orderedByVetId) { this.orderedByVetId = orderedByVetId; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public OffsetDateTime getOrderedAt() { return orderedAt; }
    public void setOrderedAt(OffsetDateTime orderedAt) { this.orderedAt = orderedAt; }
    public Boolean getFastingConfirmed() { return fastingConfirmed; }
    public void setFastingConfirmed(Boolean fastingConfirmed) { this.fastingConfirmed = fastingConfirmed; }
    public String getClinicalNotes() { return clinicalNotes; }
    public void setClinicalNotes(String clinicalNotes) { this.clinicalNotes = clinicalNotes; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
