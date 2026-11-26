package org.codeflow.veterineryproject.encounter.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "encounter_complaints", schema = "vet")
public class EncounterComplaints {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "encounter_id")
    private UUID encounterId;

    @Column(name = "complaint_code")
    private String complaintCode;

    @Column(name = "description")
    private String description;

    @Column(name = "onset_at")
    private OffsetDateTime onsetAt;

    @Column(name = "duration_text")
    private String durationText;

    @Column(name = "severity")
    private String severity;

    @Column(name = "chronology")
    private String chronology;

    @Column(name = "is_resolved")
    private Boolean isResolved;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public UUID getEncounterId() { return encounterId; }
    public void setEncounterId(UUID encounterId) { this.encounterId = encounterId; }
    public String getComplaintCode() { return complaintCode; }
    public void setComplaintCode(String complaintCode) { this.complaintCode = complaintCode; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public OffsetDateTime getOnsetAt() { return onsetAt; }
    public void setOnsetAt(OffsetDateTime onsetAt) { this.onsetAt = onsetAt; }
    public String getDurationText() { return durationText; }
    public void setDurationText(String durationText) { this.durationText = durationText; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getChronology() { return chronology; }
    public void setChronology(String chronology) { this.chronology = chronology; }
    public Boolean getIsResolved() { return isResolved; }
    public void setIsResolved(Boolean isResolved) { this.isResolved = isResolved; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
