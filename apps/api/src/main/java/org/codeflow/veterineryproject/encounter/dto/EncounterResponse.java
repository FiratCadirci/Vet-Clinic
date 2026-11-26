package org.codeflow.veterineryproject.encounter.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record EncounterResponse(
        UUID id,
        String encounterNo,
        UUID animalId,
        String animalName,
        String patientNo,
        UUID clinicId,
        UUID appointmentId,
        UUID attendingVetId,
        String encounterType,
        EncounterStatus status,
        OffsetDateTime startedAt,
        OffsetDateTime endedAt,
        String chiefComplaint,
        String history,
        String examinationSummary,
        String assessment,
        String treatmentPlan,
        String vetNotes,
        OffsetDateTime followUpAt,
        UUID createdBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<AddendumResponse> addenda,
        List<TreatmentResponse> treatments
) {
    public EncounterResponse withStatus(EncounterStatus value) {
        return new EncounterResponse(id, encounterNo, animalId, animalName, patientNo, clinicId, appointmentId, attendingVetId,
                encounterType, value, startedAt, endedAt, chiefComplaint, history, examinationSummary, assessment,
                treatmentPlan, vetNotes, followUpAt, createdBy, createdAt, updatedAt, addenda, treatments);
    }

    public EncounterResponse withContent(String chiefComplaint, String history, String examinationSummary, String assessment,
                                         String treatmentPlan, String vetNotes, OffsetDateTime followUpAt) {
        return new EncounterResponse(id, encounterNo, animalId, animalName, patientNo, clinicId, appointmentId, attendingVetId,
                encounterType, status, startedAt, endedAt, chiefComplaint, history, examinationSummary, assessment,
                treatmentPlan, vetNotes, followUpAt, createdBy, createdAt, updatedAt, addenda, treatments);
    }

    public EncounterResponse withEndedAt(OffsetDateTime value) {
        return new EncounterResponse(id, encounterNo, animalId, animalName, patientNo, clinicId, appointmentId, attendingVetId,
                encounterType, status, startedAt, value, chiefComplaint, history, examinationSummary, assessment,
                treatmentPlan, vetNotes, followUpAt, createdBy, createdAt, updatedAt, addenda, treatments);
    }

    public EncounterResponse withDetails(List<AddendumResponse> addenda, List<TreatmentResponse> treatments) {
        return new EncounterResponse(id, encounterNo, animalId, animalName, patientNo, clinicId, appointmentId, attendingVetId,
                encounterType, status, startedAt, endedAt, chiefComplaint, history, examinationSummary, assessment,
                treatmentPlan, vetNotes, followUpAt, createdBy, createdAt, updatedAt, addenda, treatments);
    }
}
