package org.codeflow.veterineryproject.imaging.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "imaging_studies", schema = "vet")
public class ImagingStudies {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "imaging_order_id")
    private UUID imagingOrderId;

    @Column(name = "procedure_definition_id")
    private UUID procedureDefinitionId;

    @Column(name = "study_no")
    private String studyNo;

    @Column(name = "body_site")
    private String bodySite;

    @Column(name = "laterality")
    private String laterality;

    @Column(name = "view_count")
    private Integer viewCount;

    @Column(name = "performed_at")
    private OffsetDateTime performedAt;

    @Column(name = "performed_by")
    private UUID performedBy;

    @Column(name = "technical_notes")
    private String technicalNotes;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public UUID getImagingOrderId() { return imagingOrderId; }
    public void setImagingOrderId(UUID imagingOrderId) { this.imagingOrderId = imagingOrderId; }
    public UUID getProcedureDefinitionId() { return procedureDefinitionId; }
    public void setProcedureDefinitionId(UUID procedureDefinitionId) { this.procedureDefinitionId = procedureDefinitionId; }
    public String getStudyNo() { return studyNo; }
    public void setStudyNo(String studyNo) { this.studyNo = studyNo; }
    public String getBodySite() { return bodySite; }
    public void setBodySite(String bodySite) { this.bodySite = bodySite; }
    public String getLaterality() { return laterality; }
    public void setLaterality(String laterality) { this.laterality = laterality; }
    public Integer getViewCount() { return viewCount; }
    public void setViewCount(Integer viewCount) { this.viewCount = viewCount; }
    public OffsetDateTime getPerformedAt() { return performedAt; }
    public void setPerformedAt(OffsetDateTime performedAt) { this.performedAt = performedAt; }
    public UUID getPerformedBy() { return performedBy; }
    public void setPerformedBy(UUID performedBy) { this.performedBy = performedBy; }
    public String getTechnicalNotes() { return technicalNotes; }
    public void setTechnicalNotes(String technicalNotes) { this.technicalNotes = technicalNotes; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
