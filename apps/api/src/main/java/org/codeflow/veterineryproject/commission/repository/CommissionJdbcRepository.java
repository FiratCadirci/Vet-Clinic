package org.codeflow.veterineryproject.commission.repository;

import org.codeflow.veterineryproject.commission.dto.CommissionDto.CommissionRecordResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Komisyon kayıtlarını okur (RLS: organizasyon + şube erişimi). Kayıtları yalnız fatura tetikleyicisi yazar. */
@Repository
public class CommissionJdbcRepository {

    private static final String SELECT = """
            select cr.id, cr.clinic_id, cr.invoice_id, i.invoice_no, cr.invoice_line_id, il.description,
                   p.product_type, il.quantity, cr.eligible_amount, cr.rate_percent, cr.commission_amount, cr.status,
                   i.issued_at, cr.calculated_at
            from vet.commission_records cr
            join vet.invoices i on i.organization_id = cr.organization_id and i.id = cr.invoice_id
            join vet.invoice_lines il on il.organization_id = cr.organization_id and il.id = cr.invoice_line_id
            left join vet.products p on p.organization_id = il.organization_id and p.id = il.product_id
            """;

    private static final RowMapper<CommissionRecordResponse> ROW = (rs, n) -> new CommissionRecordResponse(
            rs.getObject("id", UUID.class), rs.getObject("clinic_id", UUID.class), rs.getObject("invoice_id", UUID.class),
            rs.getString("invoice_no"), rs.getObject("invoice_line_id", UUID.class), rs.getString("description"),
            rs.getString("product_type"), rs.getBigDecimal("quantity"), rs.getBigDecimal("eligible_amount"),
            rs.getBigDecimal("rate_percent"), rs.getBigDecimal("commission_amount"), rs.getString("status"),
            rs.getObject("issued_at", OffsetDateTime.class), rs.getObject("calculated_at", OffsetDateTime.class));

    private final JdbcTemplate jdbc;

    public CommissionJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Organizasyonun güncel (TRIAL/ACTIVE/PAST_DUE) abonelik durumu; yoksa null. */
    public String currentSubscriptionStatus() {
        return jdbc.queryForList("""
                select status from vet.organization_subscriptions
                where status in ('TRIAL','ACTIVE','PAST_DUE')
                order by created_at desc limit 1
                """, String.class).stream().findFirst().orElse(null);
    }

    public List<CommissionRecordResponse> search(UUID clinicId, UUID invoiceId, LocalDate from, LocalDate to,
                                                 String zone, int limit, long offset) {
        List<Object> args = new ArrayList<>();
        String where = where(clinicId, invoiceId, from, to, zone, args);
        args.add(limit);
        args.add(offset);
        return jdbc.query(SELECT + where + " order by cr.calculated_at desc, i.invoice_no, il.line_no limit ? offset ?",
                ROW, args.toArray());
    }

    public long count(UUID clinicId, UUID invoiceId, LocalDate from, LocalDate to, String zone) {
        List<Object> args = new ArrayList<>();
        String where = where(clinicId, invoiceId, from, to, zone, args);
        Long n = jdbc.queryForObject("select count(*) from vet.commission_records cr" + where, Long.class, args.toArray());
        return n == null ? 0 : n;
    }

    private static String where(UUID clinicId, UUID invoiceId, LocalDate from, LocalDate to, String zone,
                                List<Object> args) {
        StringBuilder sb = new StringBuilder(" where 1 = 1");
        if (clinicId != null) {
            sb.append(" and cr.clinic_id = ?");
            args.add(clinicId);
        }
        if (invoiceId != null) {
            sb.append(" and cr.invoice_id = ?");
            args.add(invoiceId);
        }
        if (from != null) {
            sb.append(" and cr.calculated_at >= (?::date)::timestamp at time zone ?");
            args.add(from.toString());
            args.add(zone);
        }
        if (to != null) {
            sb.append(" and cr.calculated_at < ((?::date) + 1)::timestamp at time zone ?");
            args.add(to.toString());
            args.add(zone);
        }
        return sb.toString();
    }
}
