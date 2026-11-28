package org.codeflow.veterineryproject.report.dto;

import org.codeflow.veterineryproject.inventory.dto.InventoryDto.StockAlerts;
import org.codeflow.veterineryproject.report.dto.ReportDto.Overview;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Genel bakış ekranı (seçili şube). Kullanıcının yetkisi olmayan bölüm null döner:
 * kpis reports.read, todayAppointments appointments.manage, openEncounters clinical.read,
 * dueVaccinations clients.read, stockAlerts inventory.read.
 */
public final class DashboardDto {

    private DashboardDto() {
    }

    public record Dashboard(LocalDate date, UUID clinicId, Overview kpis, List<TodayAppointment> todayAppointments,
                            List<OpenEncounter> openEncounters, List<DueVaccination> dueVaccinations,
                            StockAlerts stockAlerts) {
    }

    /** Bugünkü (iptal edilmemiş) randevu. */
    public record TodayAppointment(UUID id, OffsetDateTime startsAt, OffsetDateTime endsAt, UUID animalId,
                                   String animalName, UUID clientId, String clientName, String appointmentType,
                                   UUID veterinarianId, String vetName, String status) {
    }

    /** Kapanmamış muayene (yalnız kimlik/durum; tıbbi içerik yok). */
    public record OpenEncounter(UUID id, UUID animalId, String animalName, UUID veterinarianId, String vetName,
                                String encounterType, String status, OffsetDateTime startedAt) {
    }

    /** Sonraki dozu yaklaşan ya da geçmiş aşı; sahibin adı ve telefonu hatırlatma araması içindir. */
    public record DueVaccination(UUID vaccinationId, UUID animalId, String animalName, UUID clientId,
                                 String clientName, String clientPhone, String vaccineName, LocalDate nextDueAt,
                                 boolean overdue) {
    }
}
