package org.codeflow.veterineryproject.report.service;

import org.codeflow.veterineryproject.common.time.ApiDateTime;
import org.codeflow.veterineryproject.report.dto.ReportDto.BranchPerformance;
import org.codeflow.veterineryproject.report.dto.ReportDto.ExaminationRow;
import org.codeflow.veterineryproject.report.dto.ReportDto.OccupancyRow;
import org.codeflow.veterineryproject.report.dto.ReportDto.Overview;
import org.codeflow.veterineryproject.report.dto.ReportDto.OverdueVaccination;
import org.codeflow.veterineryproject.report.dto.ReportDto.SalesReport;
import org.codeflow.veterineryproject.report.dto.ReportDto.StockMovementRow;
import org.codeflow.veterineryproject.report.model.ReportPeriod;
import org.codeflow.veterineryproject.report.repository.ReportJdbcRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Raporlama (FG-L1). Kapsam: istenen şube (kullanıcının şubelerinden biri olmalı) ya da kullanıcının tüm şubeleri.
 * Organizasyon/klinik grubu sınırını RLS uygular; tenant ve şube listesi istekten değil JWT'den gelir.
 */
@Service
public class ReportService {

    /** Geciken aşı listesinin üst sınırı (sayım ayrıca tam yapılır). */
    public static final int OVERDUE_LIST_LIMIT = 500;

    private final ReportJdbcRepository repository;
    private final OccupancyCalculator occupancy;

    public ReportService(ReportJdbcRepository repository, OccupancyCalculator occupancy) {
        this.repository = repository;
        this.occupancy = occupancy;
    }

    /** İstek metinlerinden rapor aralığı (varsayılan son 30 gün). */
    public ReportPeriod period(String from, String to) {
        return ReportPeriod.parse(from, to, OffsetDateTime.now(ApiDateTime.ZONE));
    }

    /** Raporun şube kapsamı; kullanıcının yetkili olmadığı şube istenirse 403. */
    public List<UUID> scope(String requestedClinicId, Collection<UUID> userClinicIds) {
        List<UUID> own = userClinicIds == null ? List.of() : List.copyOf(userClinicIds);
        if (requestedClinicId == null || requestedClinicId.isBlank()) {
            return own;
        }
        UUID requested;
        try {
            requested = UUID.fromString(requestedClinicId.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Geçersiz şube kimliği.");
        }
        if (!own.contains(requested)) {
            throw new AccessDeniedException("Bu şube için yetkiniz yok.");
        }
        return List.of(requested);
    }

    @Transactional(readOnly = true)
    public Overview overview(List<UUID> clinics, ReportPeriod p) {
        long exams = repository.examinationsByClinic(clinics, p).values().stream().mapToLong(Long::longValue).sum();
        return new Overview(p.from(), p.to(), exams, repository.appointmentsByStatus(clinics, p),
                occupancy.totalRate(occupancyRows(clinics, p)),
                repository.overdueVaccinationCount(clinics, today()), repository.salesTotals(clinics, p));
    }

    @Transactional(readOnly = true)
    public List<ExaminationRow> examinations(List<UUID> clinics, ReportPeriod p) {
        return repository.examinationsByDay(clinics, p);
    }

    @Transactional(readOnly = true)
    public List<OccupancyRow> occupancy(List<UUID> clinics, ReportPeriod p) {
        return occupancyRows(clinics, p);
    }

    /** Güncel durum: tarih aralığından bağımsızdır. */
    @Transactional(readOnly = true)
    public List<OverdueVaccination> overdueVaccinations(List<UUID> clinics) {
        return repository.overdueVaccinations(clinics, today(), OVERDUE_LIST_LIMIT);
    }

    @Transactional(readOnly = true)
    public List<StockMovementRow> stockMovements(List<UUID> clinics, ReportPeriod p) {
        return repository.stockMovements(clinics, p);
    }

    @Transactional(readOnly = true)
    public SalesReport sales(List<UUID> clinics, ReportPeriod p) {
        return new SalesReport(repository.salesByItem(clinics, p), repository.salesByDay(clinics, p));
    }

    @Transactional(readOnly = true)
    public List<BranchPerformance> branches(List<UUID> clinics, ReportPeriod p) {
        Map<UUID, Long> exams = repository.examinationsByClinic(clinics, p);
        Map<UUID, Long> appointments = repository.appointmentsByClinic(clinics, p);
        Map<UUID, BigDecimal> revenue = repository.revenueByClinic(clinics, p);
        List<OccupancyRow> occ = occupancyRows(clinics, p);
        return repository.clinicNames(clinics).entrySet().stream().map(e -> {
            UUID id = e.getKey();
            return new BranchPerformance(id, e.getValue(), exams.getOrDefault(id, 0L),
                    appointments.getOrDefault(id, 0L), revenue.getOrDefault(id, BigDecimal.ZERO),
                    occupancy.totalRate(occ.stream().filter(r -> r.clinicId().equals(id)).toList()));
        }).toList();
    }

    private List<OccupancyRow> occupancyRows(List<UUID> clinics, ReportPeriod p) {
        LocalDate first = p.firstDay();
        LocalDate end = p.endDayExclusive();
        return occupancy.compute(repository.workingHours(clinics, first, end), repository.bookedMinutes(clinics, p),
                first, end);
    }

    private LocalDate today() {
        return LocalDate.now(ApiDateTime.ZONE);
    }
}
