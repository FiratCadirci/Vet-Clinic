package org.codeflow.veterineryproject.platform.controller;

import org.codeflow.veterineryproject.auth.security.PlatformAdminPrincipal;
import org.codeflow.veterineryproject.platform.dto.PlatformDto.CommissionReport;
import org.codeflow.veterineryproject.platform.dto.PlatformDto.RevenueReport;
import org.codeflow.veterineryproject.platform.service.PlatformReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/** Platform yöneticisi: gelir ve komisyon raporları. Tarihler yyyy-MM-dd, iki uç dahil. */
@RestController
@RequestMapping("/api/platform/reports")
@PreAuthorize("hasAuthority('" + PlatformAdminPrincipal.AUTHORITY + "')")
public class PlatformReportController {

    private final PlatformReportService service;

    public PlatformReportController(PlatformReportService service) {
        this.service = service;
    }

    @GetMapping("/revenue")
    public RevenueReport revenue(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.revenue(from, to);
    }

    @GetMapping("/commissions")
    public CommissionReport commissions(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID organizationId) {
        return service.commissions(from, to, organizationId);
    }
}
