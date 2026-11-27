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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Tüm sorgular RLS altında çalışır. Durum geçişi/stok/komisyon kuralları veritabanı tetikleyicilerindedir. */
@Repository
public class BillingJdbcRepository {

    private static final String SUMMARY_SELECT = """
            select i.id, i.invoice_no, i.status, i.clinic_id, i.client_id, c.display_name, i.animal_id, i.issued_at,
                   i.due_at, i.currency, i.created_at, a.name as animal_name,
                   coalesce(t.grand_total, 0) as grand_total,
                   coalesce((select sum(ip.applied_amount) from vet.invoice_payments ip
                             join vet.payments p on p.organization_id = ip.organization_id and p.id = ip.payment_id
                             where ip.invoice_id = i.id and p.status = 'COMPLETED'), 0) as paid_total
            from vet.invoices i
            join vet.clients c on c.id = i.client_id
            left join vet.animals a on a.organization_id = i.organization_id and a.id = i.animal_id
            left join vet.v_invoice_totals t on t.invoice_id = i.id
            """;

    private static final RowMapper<InvoiceSummary> SUMMARY = (rs, n) -> {
        BigDecimal grand = rs.getBigDecimal("grand_total");
        BigDecimal paid = rs.getBigDecimal("paid_total");
        return new InvoiceSummary(rs.getObject("id", UUID.class), rs.getString("invoice_no"), rs.getString("status"),
                rs.getObject("clinic_id", UUID.class), rs.getObject("client_id", UUID.class),
                rs.getString("display_name"), rs.getObject("animal_id", UUID.class),
                rs.getObject("issued_at", OffsetDateTime.class), rs.getObject("due_at", LocalDate.class),
                grand, paid, grand.subtract(paid), rs.getString("currency"),
                rs.getObject("created_at", OffsetDateTime.class), rs.getString("animal_name"));
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

    /**
     * Satırı ekler. Tutarlar (net, KDV, toplam) DB'de üretilir; indirim tutarı LinePricing'den gelir. Oranla girilen
     * indirimde oran bilgi olarak saklanır, tutarla girilende 0'dır.
     */
    public void insertLine(UUID orgId, UUID invoiceId, LineRequest l, BigDecimal discountAmount) {
        jdbc.update("""
                insert into vet.invoice_lines (organization_id, invoice_id, line_no, line_type, service_id, product_id,
                                               description, quantity, unit_price, discount_rate, tax_rate, lot_id,
                                               discount_amount)
                values (?, ?, (select coalesce(max(line_no), 0) + 1 from vet.invoice_lines where invoice_id = ?),
                        ?, ?, ?, ?, ?, ?, coalesce(?, 0), coalesce(?, 20), ?, ?)
                """, orgId, invoiceId, invoiceId, l.lineType(), l.serviceId(), l.productId(), l.description().trim(),
                l.quantity(), l.unitPrice(), l.discountAmount() == null ? l.discountRate() : null, l.taxRate(),
                l.lotId(), discountAmount);
    }

    public void deleteLines(UUID invoiceId) {
        jdbc.update("delete from vet.invoice_lines where invoice_id = ?", invoiceId);
    }

    /** Kesimde FEFO dağıtımı için lotu seçilmemiş stok ürünü satırları (satır sırasıyla). */
    public List<StockLine> unallocatedStockLines(UUID invoiceId) {
        return jdbc.query("""
                select il.id, il.product_id, il.quantity, il.discount_amount
                from vet.invoice_lines il
                join vet.products p on p.organization_id = il.organization_id and p.id = il.product_id
                where il.invoice_id = ? and il.line_type = 'PRODUCT' and p.is_stock_item and il.lot_id is null
                order by il.line_no
                """, (rs, n) -> new StockLine(rs.getObject("id", UUID.class), rs.getObject("product_id", UUID.class),
                rs.getBigDecimal("quantity"), rs.getBigDecimal("discount_amount")), invoiceId);
    }

    public record StockLine(UUID id, UUID productId, BigDecimal quantity, BigDecimal discountAmount) {
    }

    /** Faturadaki ürünlerin adları (yetersiz stok mesajı için). */
    public Map<UUID, String> productNames(UUID invoiceId) {
        Map<UUID, String> names = new HashMap<>();
        jdbc.query("""
                select distinct p.id, p.name from vet.invoice_lines il
                join vet.products p on p.organization_id = il.organization_id and p.id = il.product_id
                where il.invoice_id = ?
                """, rs -> {
            names.put(rs.getObject("id", UUID.class), rs.getString("name"));
        }, invoiceId);
        return names;
    }

    /** Satırın miktarını, indirimini ve lotunu günceller (FEFO dağıtımının ilk lotu). */
    public void allocateLine(UUID lineId, BigDecimal quantity, BigDecimal discountAmount, UUID lotId) {
        jdbc.update("update vet.invoice_lines set quantity = ?, discount_amount = ?, lot_id = ? where id = ?",
                quantity, discountAmount, lotId, lineId);
    }

    /** Bölünen satırın sonraki lot parçası: aynı ürün, fiyat ve KDV; kendi miktarı, indirimi ve lotu. */
    public void insertSplitLine(UUID sourceLineId, BigDecimal quantity, BigDecimal discountAmount, UUID lotId) {
        jdbc.update("""
                insert into vet.invoice_lines (organization_id, invoice_id, line_no, line_type, service_id, product_id,
                                               description, quantity, unit_price, discount_rate, tax_rate, lot_id,
                                               discount_amount)
                select s.organization_id, s.invoice_id,
                       (select coalesce(max(line_no), 0) + 1 from vet.invoice_lines where invoice_id = s.invoice_id),
                       s.line_type, s.service_id, s.product_id, s.description, ?, s.unit_price, s.discount_rate,
                       s.tax_rate, ?, ?
                from vet.invoice_lines s where s.id = ?
                """, quantity, lotId, discountAmount, sourceLineId);
    }

    public boolean hasStockItems(UUID invoiceId) {
        Boolean v = jdbc.queryForObject("""
                select exists (select 1 from vet.invoice_lines il
                               join vet.products p on p.organization_id = il.organization_id and p.id = il.product_id
                               where il.invoice_id = ? and il.line_type = 'PRODUCT' and p.is_stock_item)
                """, Boolean.class, invoiceId);
        return Boolean.TRUE.equals(v);
    }

    /**
     * DRAFT → ISSUED tek UPDATE: numara, komisyon politikası ve stok lokasyonu ile birlikte. Tetikleyici aynı
     * ifade içinde stok çıkışını ve komisyon kayıtlarını yazar; hata olursa hiçbiri kalmaz.
     */
    public void issue(UUID invoiceId, String invoiceNo, String commissionPolicy, UUID defaultLocationId) {
        jdbc.update("""
                update vet.invoices
                set status = 'ISSUED', invoice_no = ?, commission_policy = ?,
                    inventory_location_id = coalesce(inventory_location_id, ?), updated_at = now()
                where id = ?
                """, invoiceNo, commissionPolicy, defaultLocationId, invoiceId);
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

    /** Faturanın detay alanları: notlar, muayene, kesimde uygulanan komisyon politikası. */
    public record InvoiceHeader(String notes, UUID encounterId, String commissionPolicy) {
    }

    public InvoiceHeader header(UUID invoiceId) {
        return jdbc.query("select notes, encounter_id, commission_policy from vet.invoices where id = ?",
                (rs, n) -> new InvoiceHeader(rs.getString("notes"), rs.getObject("encounter_id", UUID.class),
                        rs.getString("commission_policy")), invoiceId).stream().findFirst()
                .orElse(new InvoiceHeader(null, null, null));
    }

    public List<InvoiceSummary> search(UUID clinicId, UUID clientId, List<String> status, LocalDate from, LocalDate to,
                                       int limit, long offset) {
        List<Object> args = new ArrayList<>();
        String where = where(clinicId, clientId, status, from, to, args);
        args.add(limit);
        args.add(offset);
        return jdbc.query(SUMMARY_SELECT + where + " order by i.created_at desc, i.id limit ? offset ?", SUMMARY,
                args.toArray());
    }

    public long count(UUID clinicId, UUID clientId, List<String> status, LocalDate from, LocalDate to) {
        List<Object> args = new ArrayList<>();
        String where = where(clinicId, clientId, status, from, to, args);
        Long n = jdbc.queryForObject("select count(*) from vet.invoices i " + where, Long.class, args.toArray());
        return n == null ? 0 : n;
    }

    private static String where(UUID clinicId, UUID clientId, List<String> status, LocalDate from, LocalDate to,
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
        if (status != null && !status.isEmpty()) {
            // Birden çok durum verilebilir (?status=ISSUED&status=PAID veya ISSUED,PAID).
            sb.append(" and i.status in (").append(String.join(", ", java.util.Collections.nCopies(status.size(), "?")))
                    .append(")");
            args.addAll(status);
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
        return jdbc.query("""
                select il.*, round(il.quantity * il.unit_price, 2) as gross_amount, lot.lot_number, p.product_type
                from vet.invoice_lines il
                left join vet.inventory_lots lot on lot.organization_id = il.organization_id and lot.id = il.lot_id
                left join vet.products p on p.organization_id = il.organization_id and p.id = il.product_id
                where il.invoice_id = ? order by il.line_no
                """,
                (rs, n) -> new LineResponse(rs.getObject("id", UUID.class), rs.getInt("line_no"),
                        rs.getString("line_type"), rs.getObject("service_id", UUID.class),
                        rs.getObject("product_id", UUID.class), rs.getObject("lot_id", UUID.class),
                        rs.getString("description"), rs.getBigDecimal("quantity"), rs.getBigDecimal("unit_price"),
                        rs.getBigDecimal("discount_rate"), rs.getBigDecimal("tax_rate"),
                        rs.getBigDecimal("net_amount"), rs.getBigDecimal("tax_amount"), rs.getBigDecimal("line_total"),
                        rs.getBigDecimal("gross_amount"), rs.getBigDecimal("discount_amount"),
                        rs.getString("lot_number"), rs.getString("product_type")),
                invoiceId);
    }

    public BigDecimal[] totals(UUID invoiceId) {
        return jdbc.query("select subtotal, tax_total from vet.v_invoice_totals where invoice_id = ?",
                (rs, n) -> new BigDecimal[]{rs.getBigDecimal("subtotal"), rs.getBigDecimal("tax_total")}, invoiceId)
                .stream().findFirst().orElse(new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
    }

    /** [brüt (indirim öncesi), indirim] toplamları. */
    public BigDecimal[] grossAndDiscount(UUID invoiceId) {
        return jdbc.queryForObject("""
                select coalesce(sum(round(quantity * unit_price, 2)), 0)::numeric(14,2) as gross,
                       coalesce(sum(discount_amount), 0)::numeric(14,2) as discount
                from vet.invoice_lines where invoice_id = ?
                """, (rs, n) -> new BigDecimal[]{rs.getBigDecimal("gross"), rs.getBigDecimal("discount")}, invoiceId);
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

    /** PDF başlığı: organizasyon, şube ve müşteri iletişim bilgileri. */
    public record PdfParty(String organizationName, String legalName, String taxOffice, String taxNumber,
                           String clinicName, String clinicAddress, String clinicPhone, String clinicEmail,
                           String clientName, String clientPhone, String clientAddress) {
    }

    public PdfParty pdfParty(UUID invoiceId) {
        return jdbc.queryForObject("""
                select o.name as org_name, o.legal_name, o.tax_office, o.tax_number,
                       cl.name as clinic_name,
                       concat_ws(', ', nullif(cl.address_line, ''), nullif(cl.district, ''), nullif(cl.city, '')) as clinic_address,
                       cl.phone as clinic_phone, cl.email as clinic_email,
                       c.display_name as client_name,
                       (select cc.contact_value from vet.client_contacts cc
                         where cc.organization_id = c.organization_id and cc.client_id = c.id and cc.contact_type = 'PHONE'
                         order by cc.is_primary desc, cc.created_at limit 1) as client_phone,
                       (select concat_ws(', ', nullif(ca.address_line, ''), nullif(ca.district, ''), nullif(ca.city, ''))
                          from vet.client_addresses ca
                         where ca.organization_id = c.organization_id and ca.client_id = c.id
                         order by ca.is_primary desc, ca.created_at limit 1) as client_address
                from vet.invoices i
                join vet.organizations o on o.id = i.organization_id
                join vet.clinics cl on cl.organization_id = i.organization_id and cl.id = i.clinic_id
                join vet.clients c on c.organization_id = i.organization_id and c.id = i.client_id
                where i.id = ?
                """, (rs, n) -> new PdfParty(rs.getString("org_name"), rs.getString("legal_name"),
                rs.getString("tax_office"), rs.getString("tax_number"), rs.getString("clinic_name"),
                blankToNull(rs.getString("clinic_address")), rs.getString("clinic_phone"), rs.getString("clinic_email"),
                rs.getString("client_name"), rs.getString("client_phone"), blankToNull(rs.getString("client_address"))),
                invoiceId);
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v;
    }
}
