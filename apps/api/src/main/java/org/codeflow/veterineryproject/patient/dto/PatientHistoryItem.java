package org.codeflow.veterineryproject.patient.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Hasta geçmişi satırı. {@code type}: ENCOUNTER | VACCINATION | APPOINTMENT.
 * Muayene alanları (şikâyet, tanı, tedavi planı) yalnızca {@code clinical.read} yetkisi olan kullanıcıya ve
 * yalnızca görebildiği kayıtlarda döner; randevu satırı tıbbi metin (neden / iç not) taşımaz.
 */
public record PatientHistoryItem(
        String type,
        UUID id,
        OffsetDateTime occurredAt,
        OffsetDateTime endedAt,
        UUID clinicId,
        String clinicName,
        String status,
        String title,
        String veterinarianName,
        String chiefComplaint,
        String assessment,
        String treatmentPlan,
        Integer doseNumber,
        String batchNumber,
        LocalDate nextDueAt,
        String appointmentType,
        UUID encounterId
) {
}
