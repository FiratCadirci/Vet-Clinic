package org.codeflow.veterineryproject.report.controller;

import org.codeflow.veterineryproject.auth.security.JwtAuthenticatedUser;
import org.codeflow.veterineryproject.report.dto.DashboardDto.Dashboard;
import org.codeflow.veterineryproject.report.service.DashboardService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Genel bakış ekranı (X-Clinic-Id ile seçili şube). Bölümler kullanıcının yetkilerine göre doldurulur. */
@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardService dashboard;

    public DashboardController(DashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Dashboard dashboard(@AuthenticationPrincipal JwtAuthenticatedUser principal) {
        return dashboard.dashboard(principal.permissions() == null ? List.of() : principal.permissions());
    }
}
