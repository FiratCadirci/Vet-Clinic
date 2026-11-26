package org.codeflow.veterineryproject.imaging.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "imaging_reports", schema = "vet")
public class ImagingReports {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "imaging_study_id")
    private UUID imagingStudyId;

    @Column(name = "findings")
    private String findings;

    @Column(name = "impression")
    private String impression;

    @Column(name = "recommendations")
    private String recommendations;

    @Column(name = "reported_by")
    private UUID reportedBy;

    @Column(name = "reported_at")
    private OffsetDateTime reportedAt;

    @Column(name = "signed_at")
    private OffsetDateTime signedAt;

    @Column(name = "report_version")
    private Integer reportVersion;

    @Column(name = "is_current")
    private Boolean isCurrent;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public UUID getImagingStudyId() { return imagingStudyId; }
    public void setImagingStudyId(UUID imagingStudyId) { this.imagingStudyId = imagingStudyId; }
    public String getFindings() { return findings; }
    public void setFindings(String findings) { this.findings = findings; }
    public String getImpression() { return impression; }
    public void setImpression(String impression) { this.impression = impression; }
    public String getRecommendations() { return recommendations; }
    public void setRecommendations(String recommendations) { this.recommendations = recommendations; }
    public UUID getReportedBy() { return reportedBy; }
    public void setReportedBy(UUID reportedBy) { this.reportedBy = reportedBy; }
    public OffsetDateTime getReportedAt() { return reportedAt; }
    public void setReportedAt(OffsetDateTime reportedAt) { this.reportedAt = reportedAt; }
    public OffsetDateTime getSignedAt() { return signedAt; }
    public void setSignedAt(OffsetDateTime signedAt) { this.signedAt = signedAt; }
    public Integer getReportVersion() { return reportVersion; }
    public void setReportVersion(Integer reportVersion) { this.reportVersion = reportVersion; }
    public Boolean getIsCurrent() { return isCurrent; }
    public void setIsCurrent(Boolean isCurrent) { this.isCurrent = isCurrent; }
}
