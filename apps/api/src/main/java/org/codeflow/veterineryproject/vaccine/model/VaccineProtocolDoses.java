package org.codeflow.veterineryproject.vaccine.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "vaccine_protocol_doses", schema = "vet")
public class VaccineProtocolDoses {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "protocol_id")
    private UUID protocolId;

    @Column(name = "dose_number")
    private Integer doseNumber;

    @Column(name = "vaccine_product_id")
    private UUID vaccineProductId;

    @Column(name = "recommended_age_days")
    private Integer recommendedAgeDays;

    @Column(name = "interval_from_previous_days")
    private Integer intervalFromPreviousDays;

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public UUID getProtocolId() { return protocolId; }
    public void setProtocolId(UUID protocolId) { this.protocolId = protocolId; }
    public Integer getDoseNumber() { return doseNumber; }
    public void setDoseNumber(Integer doseNumber) { this.doseNumber = doseNumber; }
    public UUID getVaccineProductId() { return vaccineProductId; }
    public void setVaccineProductId(UUID vaccineProductId) { this.vaccineProductId = vaccineProductId; }
    public Integer getRecommendedAgeDays() { return recommendedAgeDays; }
    public void setRecommendedAgeDays(Integer recommendedAgeDays) { this.recommendedAgeDays = recommendedAgeDays; }
    public Integer getIntervalFromPreviousDays() { return intervalFromPreviousDays; }
    public void setIntervalFromPreviousDays(Integer intervalFromPreviousDays) { this.intervalFromPreviousDays = intervalFromPreviousDays; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
