package org.codeflow.veterineryproject.animal.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "animal_owners", schema = "vet")
public class AnimalOwners {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "animal_id")
    private UUID animalId;

    @Column(name = "client_id")
    private UUID clientId;

    @Column(name = "is_primary_contact")
    private Boolean isPrimaryContact;

    @Column(name = "is_legal_owner")
    private Boolean isLegalOwner;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

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
    public UUID getClientId() { return clientId; }
    public void setClientId(UUID clientId) { this.clientId = clientId; }
    public Boolean getIsPrimaryContact() { return isPrimaryContact; }
    public void setIsPrimaryContact(Boolean isPrimaryContact) { this.isPrimaryContact = isPrimaryContact; }
    public Boolean getIsLegalOwner() { return isLegalOwner; }
    public void setIsLegalOwner(Boolean isLegalOwner) { this.isLegalOwner = isLegalOwner; }
    public LocalDate getValidFrom() { return validFrom; }
    public void setValidFrom(LocalDate validFrom) { this.validFrom = validFrom; }
    public LocalDate getValidTo() { return validTo; }
    public void setValidTo(LocalDate validTo) { this.validTo = validTo; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
