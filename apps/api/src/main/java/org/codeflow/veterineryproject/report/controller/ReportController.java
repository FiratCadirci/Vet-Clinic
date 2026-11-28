package org.codeflow.veterineryproject.report.controller;

import org.codeflow.veterineryproject.auth.security.JwtAuthenticatedUser;
import org.codeflow.veterineryproject.auth.security.Permission;
import org.codeflow.veterineryproject.report.dto.ReportDto.BranchPerformance;
import org.codeflow.veterineryproject.report.dto.ReportDto.ExaminationRow;
import org.codeflow.veterineryproject.report.dto.ReportDto.OccupancyRow;
import org.codeflow.veterineryproject.report.dto.ReportDto.Overview;
import org.codeflow.veterineryproject.report.dto.ReportDto.OverdueVaccination;
import org.codeflow.veterineryproject.report.dto.ReportDto.SalesReport;
import org.codeflow.veterineryproject.report.dto.ReportDto.StockMovementRow;
import org.codeflow.veterineryproject.report.service.ReportService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Raporlar (FG-L1), reports.read. Ortak parametreler: from/to (ISO-8601 ya da yyyy-MM-dd HH:mm; from dahil, to hariç,
 * varsayılan son 30 gün, en fazla 1 yıl) ve clinicId (verilmezse kullanıcının tüm şubeleri).
 */
@RestController
@RequestMapping("/api/v1/reports")
@PreAuthorize("hasAuthority('" + Permission.REPORTS_READ + "')")
public class ReportController {

    private final ReportService reports;

    public ReportController(ReportService reports) {
        this.reports = reports;
    }

    @GetMapping("/overview")
    public Overview overview(@AuthenticationPrincipal JwtAuthenticatedUser principal,
                             @RequestParam(required = false) String from, @RequestParam(required = false) String to,
                             @RequestParam(required = false) String clinicId) {
        return reports.overview(reports.scope(clinicId, principal.clinicIds()), reports.period(from, to));
    }

    @GetMapping("/examinations")
    public List<ExaminationRow> examinations(@AuthenticationPrincipal JwtAuthenticatedUser principal,
                                             @RequestParam(required = false) String from,
                                             @RequestParam(required = false) String to,
                                             @RequestParam(required = false) String clinicId) {
        return reports.examinations(reports.scope(clinicId, principal.clinicIds()), reports.period(from, to));
    }

    @GetMapping("/occupancy")
    public List<OccupancyRow> occupancy(@AuthenticationPrincipal JwtAuthenticatedUser principal,
                                        @RequestParam(required = false) String from,
                                        @RequestParam(required = false) String to,
                                        @RequestParam(required = false) String clinicId) {
        return reports.occupancy(reports.scope(clinicId, principal.clinicIds()), reports.period(from, to));
    }

    @GetMapping("/vaccines/overdue")
    public List<OverdueVaccination> overdueVaccinations(@AuthenticationPrincipal JwtAuthenticatedUser principal,
                                                        @RequestParam(required = false) String clinicId) {
        return reports.overdueVaccinations(reports.scope(clinicId, principal.clinicIds()));
    }

    @GetMapping("/stock-movements")
    public List<StockMovementRow> stockMovements(@AuthenticationPrincipal JwtAuthenticatedUser principal,
                                                 @RequestParam(required = false) String from,
                                                 @RequestParam(required = false) String to,
                                                 @RequestParam(required = false) String clinicId) {
        return reports.stockMovements(reports.scope(clinicId, principal.clinicIds()), reports.period(from, to));
    }

    @GetMapping("/sales")
    public SalesReport sales(@AuthenticationPrincipal JwtAuthenticatedUser principal,
                             @RequestParam(required = false) String from, @RequestParam(required = false) String to,
                             @RequestParam(required = false) String clinicId) {
        return reports.sales(reports.scope(clinicId, principal.clinicIds()), reports.period(from, to));
    }

    @GetMapping("/branches")
    public List<BranchPerformance> branches(@AuthenticationPrincipal JwtAuthenticatedUser principal,
                                            @RequestParam(required = false) String from,
                                            @RequestParam(required = false) String to,
                                            @RequestParam(required = false) String clinicId) {
        return reports.branches(reports.scope(clinicId, principal.clinicIds()), reports.period(from, to));
    }
}
