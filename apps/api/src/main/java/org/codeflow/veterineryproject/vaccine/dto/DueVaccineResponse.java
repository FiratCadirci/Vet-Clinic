package org.codeflow.veterineryproject.vaccine.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** status: OVERDUE (süresi geçmiş) veya UPCOMING; daysOverdue gecikme günü (yaklaşanlar için negatif/0). */
public record DueVaccineResponse(UUID recordId, UUID animalId, String animalName, String patientNo, UUID clinicId,
                                 String vaccineName, OffsetDateTime lastAdministeredAt, LocalDate nextDueAt,
                                 String status, long daysOverdue) {
}
