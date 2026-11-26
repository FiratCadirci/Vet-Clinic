package org.codeflow.veterineryproject.patient.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Hasta listesi / hasta kartı başlığı satırı: hayvan + güncel birincil sahip + mikroçip + kayıt şubesi.
 * {@code registeredClinicId}: hastanın ilk açıldığı (erişimi verilen ilk) şube.
 */
public record PatientSummaryResponse(
        UUID id,
        String patientNo,
        String name,
        UUID speciesId,
        String speciesCode,
        String speciesName,
        UUID breedId,
        String breedName,
        String sex,
        LocalDate birthDate,
        LocalDate deceasedAt,
        String profileNotes,
        String microchipNo,
        UUID registeredClinicId,
        UUID ownerId,
        String ownerName,
        String ownerPhone,
        OffsetDateTime createdAt
) {
}
