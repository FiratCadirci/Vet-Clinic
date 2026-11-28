package org.codeflow.veterineryproject.encounter.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * weightKg / temperatureC: muayenenin son ölçülen kilo ve vücut ısısı (encounter_vitals).
 * warnings: yalnızca adım/kapatma yanıtlarında dolar; muayene türünün kancasının ürettiği uyarılar (ör. ateş).
 */
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
        List<TreatmentResponse> treatments,
        BigDecimal weightKg,
        BigDecimal temperatureC,
        List<String> warnings
) {
    /** Ölçüm ve uyarı olmadan (ilk hâli; kabul taslağı ve testler için). */
    public EncounterResponse(UUID id, String encounterNo, UUID animalId, String animalName, String patientNo, UUID clinicId,
                             UUID appointmentId, UUID attendingVetId, String encounterType, EncounterStatus status,
                             OffsetDateTime startedAt, OffsetDateTime endedAt, String chiefComplaint, String history,
                             String examinationSummary, String assessment, String treatmentPlan, String vetNotes,
                             OffsetDateTime followUpAt, UUID createdBy, OffsetDateTime createdAt, OffsetDateTime updatedAt,
                             List<AddendumResponse> addenda, List<TreatmentResponse> treatments) {
        this(id, encounterNo, animalId, animalName, patientNo, clinicId, appointmentId, attendingVetId, encounterType, status,
                startedAt, endedAt, chiefComplaint, history, examinationSummary, assessment, treatmentPlan, vetNotes,
                followUpAt, createdBy, createdAt, updatedAt, addenda, treatments, null, null, List.of());
    }

    public EncounterResponse withStatus(EncounterStatus value) {
        return new EncounterResponse(id, encounterNo, animalId, animalName, patientNo, clinicId, appointmentId, attendingVetId,
                encounterType, value, startedAt, endedAt, chiefComplaint, history, examinationSummary, assessment,
                treatmentPlan, vetNotes, followUpAt, createdBy, createdAt, updatedAt, addenda, treatments, weightKg,
                temperatureC, warnings);
    }

    public EncounterResponse withContent(String chiefComplaint, String history, String examinationSummary, String assessment,
                                         String treatmentPlan, String vetNotes, OffsetDateTime followUpAt) {
        return new EncounterResponse(id, encounterNo, animalId, animalName, patientNo, clinicId, appointmentId, attendingVetId,
                encounterType, status, startedAt, endedAt, chiefComplaint, history, examinationSummary, assessment,
                treatmentPlan, vetNotes, followUpAt, createdBy, createdAt, updatedAt, addenda, treatments, weightKg,
                temperatureC, warnings);
    }

    public EncounterResponse withEndedAt(OffsetDateTime value) {
        return new EncounterResponse(id, encounterNo, animalId, animalName, patientNo, clinicId, appointmentId, attendingVetId,
                encounterType, status, startedAt, value, chiefComplaint, history, examinationSummary, assessment,
                treatmentPlan, vetNotes, followUpAt, createdBy, createdAt, updatedAt, addenda, treatments, weightKg,
                temperatureC, warnings);
    }

    public EncounterResponse withDetails(List<AddendumResponse> addenda, List<TreatmentResponse> treatments) {
        return new EncounterResponse(id, encounterNo, animalId, animalName, patientNo, clinicId, appointmentId, attendingVetId,
                encounterType, status, startedAt, endedAt, chiefComplaint, history, examinationSummary, assessment,
                treatmentPlan, vetNotes, followUpAt, createdBy, createdAt, updatedAt, addenda, treatments, weightKg,
                temperatureC, warnings);
    }

    public EncounterResponse withVitals(BigDecimal weight, BigDecimal temperature) {
        return new EncounterResponse(id, encounterNo, animalId, animalName, patientNo, clinicId, appointmentId, attendingVetId,
                encounterType, status, startedAt, endedAt, chiefComplaint, history, examinationSummary, assessment,
                treatmentPlan, vetNotes, followUpAt, createdBy, createdAt, updatedAt, addenda, treatments, weight,
                temperature, warnings);
    }

    public EncounterResponse withWarnings(List<String> value) {
        return new EncounterResponse(id, encounterNo, animalId, animalName, patientNo, clinicId, appointmentId, attendingVetId,
                encounterType, status, startedAt, endedAt, chiefComplaint, history, examinationSummary, assessment,
                treatmentPlan, vetNotes, followUpAt, createdBy, createdAt, updatedAt, addenda, treatments, weightKg,
                temperatureC, List.copyOf(value));
    }
}
