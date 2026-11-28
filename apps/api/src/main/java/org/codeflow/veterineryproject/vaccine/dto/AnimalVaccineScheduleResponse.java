package org.codeflow.veterineryproject.vaccine.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Hastanın aşı takvimi (FG-D4): onaylı her protokol için doz durumları (DONE / PLANNED / OVERDUE), rapel ve sıradaki doz;
 * protokol dışı yapılan aşılar ve en yakın vade. pendingApproval: türün onay bekleyen taslak protokolü var.
 */
public record AnimalVaccineScheduleResponse(UUID animalId, String speciesCode, LocalDate birthDate, boolean pendingApproval,
                                            List<Item> items, List<OtherVaccination> otherVaccinations, NextDue nextDue) {

    public record Item(UUID protocolId, String protocolCode, String vaccineName, int doseCount, List<Dose> doses,
                       Booster booster, Next next) {
    }

    /** plannedAt: ilk doz için doğum + en erken yaş; sonraki dozlar için gerçekten yapılan önceki doz + aralık. */
    public record Dose(int doseNumber, String status, LocalDate plannedAt, OffsetDateTime administeredAt, UUID recordId) {
    }

    public record Booster(LocalDate dueAt, boolean overdue) {
    }

    public record Next(int doseNumber, LocalDate dueAt, boolean overdue, boolean booster) {
    }

    public record OtherVaccination(UUID recordId, String vaccineName, OffsetDateTime administeredAt, LocalDate nextDueAt) {
    }

    public record NextDue(String vaccineName, int doseNumber, LocalDate dueAt, boolean overdue) {
    }
}
