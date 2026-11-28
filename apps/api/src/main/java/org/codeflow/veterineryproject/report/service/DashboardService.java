package org.codeflow.veterineryproject.report.service;

import org.codeflow.veterineryproject.auth.security.Permission;
import org.codeflow.veterineryproject.common.tenant.TenantContext;
import org.codeflow.veterineryproject.common.time.ApiDateTime;
import org.codeflow.veterineryproject.inventory.dto.InventoryDto.StockAlerts;
import org.codeflow.veterineryproject.inventory.service.StockQueryService;
import org.codeflow.veterineryproject.report.dto.DashboardDto.Dashboard;
import org.codeflow.veterineryproject.report.dto.DashboardDto.DueVaccination;
import org.codeflow.veterineryproject.report.dto.DashboardDto.OpenEncounter;
import org.codeflow.veterineryproject.report.dto.DashboardDto.TodayAppointment;
import org.codeflow.veterineryproject.report.dto.ReportDto.Overview;
import org.codeflow.veterineryproject.report.model.ReportPeriod;
import org.codeflow.veterineryproject.report.repository.DashboardJdbcRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Genel bakış (Facade): seçili şubenin günlük özetini rapor, randevu, muayene, aşı ve stok kaynaklarından tek yanıtta
 * toplar. Her bölüm yalnız ilgili yetkiye sahip kullanıcıya hesaplanır; yetkisi olmayan bölüm null döner.
 */
@Service
public class DashboardService {

    /** KPI kartlarının aralığı (ekranda "Son 30 gün"). */
    public static final int KPI_DAYS = ReportPeriod.DEFAULT_DAYS;
    /** "Yaklaşan" aşı penceresi (bugünden itibaren gün). */
    public static final int DUE_VACCINE_DAYS = 14;
    /** Liste bölümlerinin üst sınırı (FOG-8). */
    public static final int LIST_LIMIT = 200;

    private final ReportService reports;
    private final DashboardJdbcRepository repository;
    private final StockQueryService stock;

    public DashboardService(ReportService reports, DashboardJdbcRepository repository, StockQueryService stock) {
        this.reports = reports;
        this.repository = repository;
        this.stock = stock;
    }

    @Transactional(readOnly = true)
    public Dashboard dashboard(Collection<String> permissions) {
        UUID clinicId = TenantContext.require().clinicId();
        OffsetDateTime now = OffsetDateTime.now(ApiDateTime.ZONE);
        LocalDate today = now.toLocalDate();
        OffsetDateTime dayStart = today.atStartOfDay(ApiDateTime.ZONE).toOffsetDateTime();
        OffsetDateTime dayEnd = today.plusDays(1).atStartOfDay(ApiDateTime.ZONE).toOffsetDateTime();

        Overview kpis = permissions.contains(Permission.REPORTS_READ)
                ? reports.overview(List.of(clinicId), new ReportPeriod(now.minusDays(KPI_DAYS), now)) : null;
        List<TodayAppointment> appointments = permissions.contains(Permission.APPOINTMENTS_MANAGE)
                ? repository.appointments(clinicId, dayStart, dayEnd, LIST_LIMIT) : null;
        List<OpenEncounter> encounters = permissions.contains(Permission.CLINICAL_READ)
                ? repository.openEncounters(clinicId, LIST_LIMIT) : null;
        List<DueVaccination> vaccines = permissions.contains(Permission.CLIENTS_READ)
                ? repository.dueVaccinations(clinicId, today, today.plusDays(DUE_VACCINE_DAYS + 1L), LIST_LIMIT) : null;
        StockAlerts alerts = permissions.contains(Permission.INVENTORY_READ)
                ? stock.alerts(false, List.of(clinicId), StockQueryService.DEFAULT_EXPIRY_DAYS) : null;
        return new Dashboard(today, clinicId, kpis, appointments, encounters, vaccines, alerts);
    }
}
