package org.codeflow.veterineryproject.billing.repository;

import org.codeflow.veterineryproject.billing.dto.BillingDto.InvoiceSummary;
import org.codeflow.veterineryproject.billing.dto.BillingDto.LineRequest;
import org.codeflow.veterineryproject.billing.dto.BillingDto.LineResponse;
import org.codeflow.veterineryproject.billing.dto.BillingDto.MethodTotal;
import org.codeflow.veterineryproject.billing.dto.BillingDto.PaymentResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Tüm sorgular RLS altında çalışır. Durum geçişi/stok/komisyon kuralları veritabanı tetikleyicilerindedir. */
@Repository
public class BillingJdbcRepository {

    private static final String SUMMARY_SELECT = """
            select i.id, i.invoice_no, i.status, i.clinic_id, i.client_id, c.display_name, i.animal_id, i.issued_at,
                   i.due_at, i.currency,
                   coalesce(t.grand_total, 0) as grand_total,
                   coalesce((select sum(ip.applied_amount) from vet.invoice_payments ip
                             join vet.payments p on p.organization_id = ip.organization_id and p.id = ip.payment_id
                             where ip.invoice_id = i.id and p.status = 'COMPLETED'), 0) as paid_total
            from vet.invoices i
            join vet.clients c on c.id = i.client_id
            left join vet.v_invoice_totals t on t.invoice_id = i.id
            """;

    private static final RowMapper<InvoiceSummary> SUMMARY = (rs, n) -> {
        BigDecimal grand = rs.getBigDecimal("grand_total");
        BigDecimal paid = rs.getBigDecimal("paid_total");
        return new InvoiceSummary(rs.getObject("id", UUID.class), rs.getString("invoice_no"), rs.getString("status"),
                rs.getObject("clinic_id", UUID.class), rs.getObject("client_id", UUID.class),
                rs.getString("display_name"), rs.getObject("animal_id", UUID.class),
                rs.getObject("issued_at", OffsetDateTime.class), rs.getObject("due_at", LocalDate.class),
                grand, paid, grand.subtract(paid), rs.getString("currency"));
    };

    private final JdbcTemplate jdbc;

    public BillingJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insertInvoice(UUID id, UUID orgId, UUID clinicId, UUID locationId, UUID clientId, UUID animalId,
                              UUID encounterId, String invoiceNo, LocalDate dueAt, String notes, UUID userId) {
        jdbc.update("""
                insert into vet.invoices (id, organization_id, clinic_id, inventory_location_id, client_id, animal_id,
                                          encounter_id, invoice_no, due_at, notes, created_by)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, orgId, clinicId, locationId, clientId, animalId, encounterId, invoiceNo, dueAt, notes, userId);
    }

    public void insertLine(UUID orgId, UUID invoiceId, LineRequest l) {
        jdbc.update("""
                insert into vet.invoice_lines (organization_id, invoice_id, line_no, line_type, service_id, product_id,
                                               description, quantity, unit_price, discount_rate, tax_rate, lot_id)
                values (?, ?, (select coalesce(max(line_no), 0) + 1 from vet.invoice_lines where invoice_id = ?),
                        ?, ?, ?, ?, ?, ?, coalesce(?, 0), coalesce(?, 20), ?)
                """, orgId, invoiceId, invoiceId, l.lineType(), l.serviceId(), l.productId(), l.description().trim(),
                l.quantity(), l.unitPrice(), l.discountRate(), l.taxRate(), l.lotId());
    }

    public Optional<InvoiceSummary> findSummary(UUID id) {
        return jdbc.query(SUMMARY_SELECT + " where i.id = ?", SUMMARY, id).stream().findFirst();
    }

    /** Satırı kilitleyerek okur; eşzamanlı ödemelerde fazla ödeme riskini önler. */
    public Optional<InvoiceLock> lock(UUID id) {
        return jdbc.query("select id, status, client_id, clinic_id, invoice_type from vet.invoices where id = ? for update",
                (rs, n) -> new InvoiceLock(rs.getObject("id", UUID.class), rs.getString("status"),
                        rs.getObject("client_id", UUID.class), rs.getObject("clinic_id", UUID.class),
                        rs.getString("invoice_type")), id).stream().findFirst();
    }

    public record InvoiceLock(UUID id, String status, UUID clientId, UUID clinicId, String invoiceType) {
    }

    public List<InvoiceSummary> search(UUID clinicId, UUID clientId, String status, LocalDate from, LocalDate to,
                                       int limit, long offset) {
        List<Object> args = new ArrayList<>();
        String where = where(clinicId, clientId, status, from, to, args);
        args.add(limit);
        args.add(offset);
        return jdbc.query(SUMMARY_SELECT + where + " order by i.created_at desc, i.id limit ? offset ?", SUMMARY,
                args.toArray());
    }

    public long count(UUID clinicId, UUID clientId, String status, LocalDate from, LocalDate to) {
        List<Object> args = new ArrayList<>();
        String where = where(clinicId, clientId, status, from, to, args);
        Long n = jdbc.queryForObject("select count(*) from vet.invoices i " + where, Long.class, args.toArray());
        return n == null ? 0 : n;
    }

    private static String where(UUID clinicId, UUID clientId, String status, LocalDate from, LocalDate to,
                                List<Object> args) {
        StringBuilder sb = new StringBuilder(" where 1 = 1");
        if (clinicId != null) {
            sb.append(" and i.clinic_id = ?");
            args.add(clinicId);
        }
        if (clientId != null) {
            sb.append(" and i.client_id = ?");
            args.add(clientId);
        }
        if (status != null) {
            sb.append(" and i.status = ?");
            args.add(status);
        }
        if (from != null) {
            sb.append(" and i.created_at >= ?::date");
            args.add(from.toString());
        }
        if (to != null) {
            sb.append(" and i.created_at < (?::date + 1)");
            args.add(to.toString());
        }
        return sb.toString();
    }

    public List<LineResponse> lines(UUID invoiceId) {
        return jdbc.query("select * from vet.invoice_lines where invoice_id = ? order by line_no",
                (rs, n) -> new LineResponse(rs.getObject("id", UUID.class), rs.getInt("line_no"),
                        rs.getString("line_type"), rs.getObject("service_id", UUID.class),
                        rs.getObject("product_id", UUID.class), rs.getObject("lot_id", UUID.class),
                        rs.getString("description"), rs.getBigDecimal("quantity"), rs.getBigDecimal("unit_price"),
                        rs.getBigDecimal("discount_rate"), rs.getBigDecimal("tax_rate"),
                        rs.getBigDecimal("net_amount"), rs.getBigDecimal("tax_amount"), rs.getBigDecimal("line_total")),
                invoiceId);
    }

    public BigDecimal[] totals(UUID invoiceId) {
        return jdbc.query("select subtotal, tax_total from vet.v_invoice_totals where invoice_id = ?",
                (rs, n) -> new BigDecimal[]{rs.getBigDecimal("subtotal"), rs.getBigDecimal("tax_total")}, invoiceId)
                .stream().findFirst().orElse(new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
    }

    public BigDecimal grandTotal(UUID invoiceId) {
        return jdbc.query("select grand_total from vet.v_invoice_totals where invoice_id = ?",
                (rs, n) -> rs.getBigDecimal("grand_total"), invoiceId).stream().findFirst().orElse(BigDecimal.ZERO);
    }

    public BigDecimal paidTotal(UUID invoiceId) {
        BigDecimal v = jdbc.queryForObject("""
                select coalesce(sum(ip.applied_amount), 0) from vet.invoice_payments ip
                join vet.payments p on p.organization_id = ip.organization_id and p.id = ip.payment_id
                where ip.invoice_id = ? and p.status = 'COMPLETED'
                """, BigDecimal.class, invoiceId);
        return v == null ? BigDecimal.ZERO : v;
    }

    public void updateStatus(UUID invoiceId, String status) {
        jdbc.update("update vet.invoices set status = ?, updated_at = now() where id = ?", status, invoiceId);
    }

    public void insertPayment(UUID id, UUID orgId, UUID clientId, UUID clinicId, String paymentNo, String method,
                              BigDecimal amount, String currency, String ref, String notes, UUID userId) {
        jdbc.update("""
                insert into vet.payments (id, organization_id, client_id, clinic_id, payment_no, payment_method, amount,
                                          currency, reference_no, notes, received_by)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, orgId, clientId, clinicId, paymentNo, method, amount, currency, ref, notes, userId);
    }

    public void applyPayment(UUID orgId, UUID invoiceId, UUID paymentId, BigDecimal amount) {
        jdbc.update("insert into vet.invoice_payments (organization_id, invoice_id, payment_id, applied_amount) values (?, ?, ?, ?)",
                orgId, invoiceId, paymentId, amount);
    }

    public String currency(UUID invoiceId) {
        return jdbc.queryForObject("select currency from vet.invoices where id = ?", String.class, invoiceId);
    }

    public List<PaymentResponse> payments(UUID invoiceId) {
        return jdbc.query("""
                select p.id, p.payment_no, p.payment_method, ip.applied_amount, p.paid_at, p.reference_no
                from vet.invoice_payments ip
                join vet.payments p on p.organization_id = ip.organization_id and p.id = ip.payment_id
                where ip.invoice_id = ? and p.status = 'COMPLETED'
                order by p.paid_at, p.id
                """, (rs, n) -> new PaymentResponse(rs.getObject("id", UUID.class), rs.getString("payment_no"),
                rs.getString("payment_method"), rs.getBigDecimal("applied_amount"),
                rs.getObject("paid_at", OffsetDateTime.class), rs.getString("reference_no")), invoiceId);
    }

    public boolean clientVisible(UUID clientId) {
        return !jdbc.queryForList("select 1 from vet.clients where id = ?", clientId).isEmpty();
    }

    public List<InvoiceSummary> openInvoices(UUID clientId) {
        return jdbc.query(SUMMARY_SELECT + """
                 where i.client_id = ? and i.invoice_type = 'SALES' and i.status in ('ISSUED','PARTIALLY_PAID','OVERDUE')
                 order by i.issued_at, i.id
                """, SUMMARY, clientId);
    }

    public List<MethodTotal> collectedByMethod(LocalDate date, String zone, UUID clinicId) {
        return jdbc.query("""
                select p.payment_method, count(*) as cnt, sum(p.amount) as total
                from vet.payments p
                where p.status = 'COMPLETED'
                  and p.paid_at >= (?::date)::timestamp at time zone ?
                  and p.paid_at < ((?::date) + 1)::timestamp at time zone ?
                  and (?::uuid is null or p.clinic_id = ?::uuid)
                group by p.payment_method order by p.payment_method
                """, (rs, n) -> new MethodTotal(rs.getString("payment_method"), rs.getLong("cnt"),
                rs.getBigDecimal("total")), date.toString(), zone, date.toString(), zone, clinicId, clinicId);
    }

    /** [adet, toplam] — o gün düzenlenen (kesilen) faturalar. */
    public Object[] issuedOn(LocalDate date, String zone, UUID clinicId) {
        return jdbc.queryForObject("""
                select count(*) as cnt, coalesce(sum(t.grand_total), 0) as total
                from vet.invoices i
                left join vet.v_invoice_totals t on t.invoice_id = i.id
                where i.invoice_type = 'SALES' and i.status <> 'DRAFT' and i.status <> 'VOID'
                  and i.issued_at >= (?::date)::timestamp at time zone ?
                  and i.issued_at < ((?::date) + 1)::timestamp at time zone ?
                  and (?::uuid is null or i.clinic_id = ?::uuid)
                """, (rs, n) -> new Object[]{rs.getLong("cnt"), rs.getBigDecimal("total")},
                date.toString(), zone, date.toString(), zone, clinicId, clinicId);
    }

    public BigDecimal outstanding(UUID clinicId) {
        BigDecimal v = jdbc.queryForObject("""
                select coalesce(sum(t.grand_total), 0) - coalesce(sum((select coalesce(sum(ip.applied_amount), 0)
                         from vet.invoice_payments ip join vet.payments p
                           on p.organization_id = ip.organization_id and p.id = ip.payment_id
                         where ip.invoice_id = i.id and p.status = 'COMPLETED')), 0)
                from vet.invoices i
                join vet.v_invoice_totals t on t.invoice_id = i.id
                where i.invoice_type = 'SALES' and i.status in ('ISSUED','PARTIALLY_PAID','OVERDUE')
                  and (?::uuid is null or i.clinic_id = ?::uuid)
                """, BigDecimal.class, clinicId, clinicId);
        return v == null ? BigDecimal.ZERO : v;
    }
}
