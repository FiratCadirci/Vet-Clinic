package org.codeflow.veterineryproject.encounter.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "encounter_diagnoses", schema = "vet")
public class EncounterDiagnoses {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "encounter_id")
    private UUID encounterId;

    @Column(name = "diagnosis_id")
    private UUID diagnosisId;

    @Column(name = "notes")
    private String notes;

    @Column(name = "diagnosed_at")
    private OffsetDateTime diagnosedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public UUID getEncounterId() { return encounterId; }
    public void setEncounterId(UUID encounterId) { this.encounterId = encounterId; }
    public UUID getDiagnosisId() { return diagnosisId; }
    public void setDiagnosisId(UUID diagnosisId) { this.diagnosisId = diagnosisId; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public OffsetDateTime getDiagnosedAt() { return diagnosedAt; }
    public void setDiagnosedAt(OffsetDateTime diagnosedAt) { this.diagnosedAt = diagnosedAt; }
}
