package org.codeflow.veterineryproject.platform.repository;

import org.codeflow.veterineryproject.platform.dto.PlatformDto.MonthlyCommission;
import org.codeflow.veterineryproject.platform.dto.PlatformDto.MonthlySubscriptionRevenue;
import org.codeflow.veterineryproject.platform.dto.PlatformDto.OrganizationCommission;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Platform gelir ve komisyon raporları (organizasyonlar arası; V4 platform SELECT politikaları). Ay, Türkiye saatine
 * göre hesaplanır. Aralık [from, to) yarı açıktır; null sınır uygulanmaz.
 */
@Repository
public class PlatformReportRepository {

    private static final String MONTH = "to_char(date_trunc('month', %s at time zone 'Europe/Istanbul'), 'YYYY-MM')";

    private final JdbcTemplate jdbc;

    public PlatformReportRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Başarılı abonelik ödemeleri, ödeme tarihine göre aylık. */
    public List<MonthlySubscriptionRevenue> subscriptionRevenueByMonth(OffsetDateTime from, OffsetDateTime to) {
        List<Object> params = new ArrayList<>();
        String month = MONTH.formatted("e.processed_at");
        String sql = "select " + month + " as month, count(*) as payment_count, sum(e.amount) as amount"
                + " from vet.subscription_billing_events e where e.status = 'SUCCEEDED'"
                + range("e.processed_at", from, to, params)
                + " group by 1 order by 1";
        return jdbc.query(sql, (rs, i) -> new MonthlySubscriptionRevenue(rs.getString("month"),
                rs.getInt("payment_count"), rs.getBigDecimal("amount")), params.toArray());
    }

    /** İptal edilmemiş (VOID hariç) komisyon kayıtları, hesaplama tarihine göre aylık. */
    public List<MonthlyCommission> commissionsByMonth(OffsetDateTime from, OffsetDateTime to) {
        List<Object> params = new ArrayList<>();
        String month = MONTH.formatted("r.calculated_at");
        String sql = "select " + month + " as month, sum(r.eligible_amount) as eligible, sum(r.commission_amount) as commission"
                + " from vet.commission_records r where r.status <> 'VOID'"
                + range("r.calculated_at", from, to, params)
                + " group by 1 order by 1";
        return jdbc.query(sql, (rs, i) -> new MonthlyCommission(rs.getString("month"), rs.getBigDecimal("eligible"),
                rs.getBigDecimal("commission")), params.toArray());
    }

    /** Klinik (organizasyon) bazında komisyon toplamları; isteğe bağlı tek organizasyon. */
    public List<OrganizationCommission> commissionsByOrganization(OffsetDateTime from, OffsetDateTime to,
                                                                  UUID organizationId) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                select o.id, o.code, o.name, count(*) as record_count, sum(r.eligible_amount) as eligible,
                       sum(r.commission_amount) as commission
                from vet.commission_records r
                join vet.organizations o on o.id = r.organization_id
                where r.status <> 'VOID'
                """);
        sql.append(range("r.calculated_at", from, to, params));
        if (organizationId != null) {
            sql.append(" and r.organization_id = ?");
            params.add(organizationId);
        }
        sql.append(" group by o.id, o.code, o.name order by commission desc, o.name");
        return jdbc.query(sql.toString(), (rs, i) -> new OrganizationCommission(rs.getObject("id", UUID.class),
                rs.getObject("code", Integer.class), rs.getString("name"), rs.getInt("record_count"),
                rs.getBigDecimal("eligible"), rs.getBigDecimal("commission")), params.toArray());
    }

    private static String range(String column, OffsetDateTime from, OffsetDateTime to, List<Object> params) {
        StringBuilder clause = new StringBuilder();
        if (from != null) {
            clause.append(" and ").append(column).append(" >= ?");
            params.add(from);
        }
        if (to != null) {
            clause.append(" and ").append(column).append(" < ?");
            params.add(to);
        }
        return clause.toString();
    }
}
