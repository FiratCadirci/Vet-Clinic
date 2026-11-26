package org.codeflow.veterineryproject.vaccine.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "vaccine_records", schema = "vet")
public class VaccineRecords {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "animal_id")
    private UUID animalId;

    @Column(name = "encounter_id")
    private UUID encounterId;

    @Column(name = "vaccine_product_id")
    private UUID vaccineProductId;

    @Column(name = "lot_id")
    private UUID lotId;

    @Column(name = "care_schedule_event_id")
    private UUID careScheduleEventId;

    @Column(name = "administered_at")
    private OffsetDateTime administeredAt;

    @Column(name = "dose")
    private BigDecimal dose;

    @Column(name = "dose_unit")
    private String doseUnit;

    @Column(name = "route")
    private String route;

    @Column(name = "administration_site")
    private String administrationSite;

    @Column(name = "administered_by")
    private UUID administeredBy;

    @Column(name = "next_due_at")
    private LocalDate nextDueAt;

    @Column(name = "certificate_no")
    private String certificateNo;

    @Column(name = "adverse_reaction")
    private String adverseReaction;

    @Column(name = "inventory_transaction_id")
    private UUID inventoryTransactionId;

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public UUID getAnimalId() { return animalId; }
    public void setAnimalId(UUID animalId) { this.animalId = animalId; }
    public UUID getEncounterId() { return encounterId; }
    public void setEncounterId(UUID encounterId) { this.encounterId = encounterId; }
    public UUID getVaccineProductId() { return vaccineProductId; }
    public void setVaccineProductId(UUID vaccineProductId) { this.vaccineProductId = vaccineProductId; }
    public UUID getLotId() { return lotId; }
    public void setLotId(UUID lotId) { this.lotId = lotId; }
    public UUID getCareScheduleEventId() { return careScheduleEventId; }
    public void setCareScheduleEventId(UUID careScheduleEventId) { this.careScheduleEventId = careScheduleEventId; }
    public OffsetDateTime getAdministeredAt() { return administeredAt; }
    public void setAdministeredAt(OffsetDateTime administeredAt) { this.administeredAt = administeredAt; }
    public BigDecimal getDose() { return dose; }
    public void setDose(BigDecimal dose) { this.dose = dose; }
    public String getDoseUnit() { return doseUnit; }
    public void setDoseUnit(String doseUnit) { this.doseUnit = doseUnit; }
    public String getRoute() { return route; }
    public void setRoute(String route) { this.route = route; }
    public String getAdministrationSite() { return administrationSite; }
    public void setAdministrationSite(String administrationSite) { this.administrationSite = administrationSite; }
    public UUID getAdministeredBy() { return administeredBy; }
    public void setAdministeredBy(UUID administeredBy) { this.administeredBy = administeredBy; }
    public LocalDate getNextDueAt() { return nextDueAt; }
    public void setNextDueAt(LocalDate nextDueAt) { this.nextDueAt = nextDueAt; }
    public String getCertificateNo() { return certificateNo; }
    public void setCertificateNo(String certificateNo) { this.certificateNo = certificateNo; }
    public String getAdverseReaction() { return adverseReaction; }
    public void setAdverseReaction(String adverseReaction) { this.adverseReaction = adverseReaction; }
    public UUID getInventoryTransactionId() { return inventoryTransactionId; }
    public void setInventoryTransactionId(UUID inventoryTransactionId) { this.inventoryTransactionId = inventoryTransactionId; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
