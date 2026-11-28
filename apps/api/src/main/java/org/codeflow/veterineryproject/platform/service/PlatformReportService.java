package org.codeflow.veterineryproject.platform.service;

import org.codeflow.veterineryproject.common.time.ApiDateTime;
import org.codeflow.veterineryproject.platform.dto.PlatformDto.CommissionReport;
import org.codeflow.veterineryproject.platform.dto.PlatformDto.MonthlyCommission;
import org.codeflow.veterineryproject.platform.dto.PlatformDto.MonthlySubscriptionRevenue;
import org.codeflow.veterineryproject.platform.dto.PlatformDto.OrganizationCommission;
import org.codeflow.veterineryproject.platform.dto.PlatformDto.RevenueReport;
import org.codeflow.veterineryproject.platform.dto.PlatformDto.RevenueTotals;
import org.codeflow.veterineryproject.platform.repository.PlatformReportRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * Senaryo 6 / FG-K5: platform gelir raporu (abonelik ödemeleri + komisyon, aylık) ve klinik bazında komisyon raporu.
 * Tarih filtresi gün olarak verilir ve iki uç dahildir (Türkiye saatiyle).
 */
@Service
public class PlatformReportService {

    private final PlatformReportRepository repository;

    public PlatformReportService(PlatformReportRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public RevenueReport revenue(LocalDate from, LocalDate to) {
        validate(from, to);
        List<MonthlySubscriptionRevenue> subscriptions = repository.subscriptionRevenueByMonth(start(from), endExclusive(to));
        List<MonthlyCommission> commissions = repository.commissionsByMonth(start(from), endExclusive(to));
        BigDecimal subscriptionTotal = sum(subscriptions, MonthlySubscriptionRevenue::amount);
        BigDecimal commissionTotal = sum(commissions, MonthlyCommission::commissionAmount);
        return new RevenueReport(from, to, new RevenueTotals(subscriptionTotal, commissionTotal,
                subscriptionTotal.add(commissionTotal)), subscriptions, commissions);
    }

    @Transactional(readOnly = true)
    public CommissionReport commissions(LocalDate from, LocalDate to, UUID organizationId) {
        validate(from, to);
        List<OrganizationCommission> rows = repository.commissionsByOrganization(start(from), endExclusive(to), organizationId);
        return new CommissionReport(from, to, sum(rows, OrganizationCommission::eligibleAmount),
                sum(rows, OrganizationCommission::commissionAmount), rows);
    }

    private static void validate(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("Başlangıç tarihi bitiş tarihinden sonra olamaz.");
        }
    }

    private static OffsetDateTime start(LocalDate day) {
        return day == null ? null : day.atStartOfDay(ApiDateTime.ZONE).toOffsetDateTime();
    }

    private static OffsetDateTime endExclusive(LocalDate day) {
        return day == null ? null : day.plusDays(1).atStartOfDay(ApiDateTime.ZONE).toOffsetDateTime();
    }

    private static <T> BigDecimal sum(List<T> rows, Function<T, BigDecimal> value) {
        return rows.stream().map(value).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
