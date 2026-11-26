package org.codeflow.veterineryproject.encounter.model;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "encounters", schema = "vet")
public class Encounter {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "clinic_id", nullable = false)
    private UUID clinicId;

    @Column(name = "animal_id", nullable = false)
    private UUID animalId;

    @Column(name = "appointment_id")
    private UUID appointmentId;

    @Column(name = "attending_vet_id", nullable = false)
    private UUID attendingVetId;

    @Column(name = "encounter_no", nullable = false)
    private String encounterNo;

    @Column(name = "encounter_type", nullable = false)
    private String encounterType;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "ended_at")
    private OffsetDateTime endedAt;

    @Column(name = "chief_complaint")
    private String chiefComplaint;

    @Column(name = "history_text")
    private String historyText;

    @Column(name = "examination_summary")
    private String examinationSummary;

    @Column(name = "assessment_text")
    private String assessmentText;

    @Column(name = "treatment_plan")
    private String treatmentPlan;

    @Column(name = "follow_up_at")
    private OffsetDateTime followUpAt;

    @Column(name = "signed_at")
    private OffsetDateTime signedAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public UUID getClinicId() { return clinicId; }
    public void setClinicId(UUID clinicId) { this.clinicId = clinicId; }
    public UUID getAnimalId() { return animalId; }
    public void setAnimalId(UUID animalId) { this.animalId = animalId; }
    public UUID getAppointmentId() { return appointmentId; }
    public void setAppointmentId(UUID appointmentId) { this.appointmentId = appointmentId; }
    public UUID getAttendingVetId() { return attendingVetId; }
    public void setAttendingVetId(UUID attendingVetId) { this.attendingVetId = attendingVetId; }
    public String getEncounterNo() { return encounterNo; }
    public void setEncounterNo(String encounterNo) { this.encounterNo = encounterNo; }
    public String getEncounterType() { return encounterType; }
    public void setEncounterType(String encounterType) { this.encounterType = encounterType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(OffsetDateTime startedAt) { this.startedAt = startedAt; }
    public OffsetDateTime getEndedAt() { return endedAt; }
    public void setEndedAt(OffsetDateTime endedAt) { this.endedAt = endedAt; }
    public String getChiefComplaint() { return chiefComplaint; }
    public void setChiefComplaint(String chiefComplaint) { this.chiefComplaint = chiefComplaint; }
    public String getHistoryText() { return historyText; }
    public void setHistoryText(String historyText) { this.historyText = historyText; }
    public String getExaminationSummary() { return examinationSummary; }
    public void setExaminationSummary(String examinationSummary) { this.examinationSummary = examinationSummary; }
    public String getAssessmentText() { return assessmentText; }
    public void setAssessmentText(String assessmentText) { this.assessmentText = assessmentText; }
    public String getTreatmentPlan() { return treatmentPlan; }
    public void setTreatmentPlan(String treatmentPlan) { this.treatmentPlan = treatmentPlan; }
    public OffsetDateTime getFollowUpAt() { return followUpAt; }
    public void setFollowUpAt(OffsetDateTime followUpAt) { this.followUpAt = followUpAt; }
    public OffsetDateTime getSignedAt() { return signedAt; }
    public void setSignedAt(OffsetDateTime signedAt) { this.signedAt = signedAt; }
    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
