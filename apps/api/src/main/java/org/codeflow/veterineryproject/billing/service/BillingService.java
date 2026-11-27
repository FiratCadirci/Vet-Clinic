package org.codeflow.veterineryproject.billing.service;

import jakarta.persistence.EntityNotFoundException;
import org.codeflow.veterineryproject.billing.dto.BillingDto.ClientBalance;
import org.codeflow.veterineryproject.billing.dto.BillingDto.CreateInvoiceRequest;
import org.codeflow.veterineryproject.billing.dto.BillingDto.DailyCashSummary;
import org.codeflow.veterineryproject.billing.dto.BillingDto.InvoiceResponse;
import org.codeflow.veterineryproject.billing.dto.BillingDto.InvoiceSummary;
import org.codeflow.veterineryproject.billing.dto.BillingDto.LineRequest;
import org.codeflow.veterineryproject.billing.dto.BillingDto.MethodTotal;
import org.codeflow.veterineryproject.billing.dto.BillingDto.PaymentRequest;
import org.codeflow.veterineryproject.billing.pdf.InvoicePdfRenderer;
import org.codeflow.veterineryproject.billing.repository.BillingJdbcRepository;
import org.codeflow.veterineryproject.billing.repository.BillingJdbcRepository.InvoiceHeader;
import org.codeflow.veterineryproject.billing.repository.BillingJdbcRepository.InvoiceLock;
import org.codeflow.veterineryproject.commission.policy.CommissionPolicy;
import org.codeflow.veterineryproject.commission.service.CommissionService;
import org.codeflow.veterineryproject.common.api.BusinessRuleException;
import org.codeflow.veterineryproject.common.api.ConflictException;
import org.codeflow.veterineryproject.common.audit.AuditRecorder;
import org.codeflow.veterineryproject.common.event.DomainEvents.InvoiceIssuedEvent;
import org.codeflow.veterineryproject.common.pagination.PageResponse;
import org.codeflow.veterineryproject.common.pagination.PaginationUtils;
import org.codeflow.veterineryproject.common.sequence.SequenceCounterRepository;
import org.codeflow.veterineryproject.common.tenant.TenantContext;
import org.codeflow.veterineryproject.inventory.service.StockConsumptionService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class BillingService {

    static final String ZONE = "Europe/Istanbul";
    private static final Set<String> PAYABLE = Set.of("ISSUED", "PARTIALLY_PAID", "OVERDUE");
    private static final String NOT_FOUND = "Fatura bulunamadı.";
    private static final String DRAFT = "DRAFT";
    /** Numara kesimde verilir; taslakta (invoice_no NOT NULL + organizasyonda tekil) geçici yer tutucu kullanılır. */
    private static final String DRAFT_NUMBER_PREFIX = "F-TASLAK-";
    private static final String INVOICE_NUMBER_FORMAT = "F-%06d";
    private static final String INVOICE_COUNTER = "INVOICE";
    private static final String STOCK_GUARD_SQLSTATE = "23514";
    private static final Pattern GUARD_PRODUCT = Pattern.compile("Yetersiz stok\\. Ürün ([0-9a-fA-F-]{36})");

    private final BillingJdbcRepository repository;
    private final SequenceCounterRepository sequences;
    private final AuditRecorder audit;
    private final InvoiceLotAllocator lotAllocator;
    private final StockConsumptionService stock;
    private final CommissionService commissions;
    private final ApplicationEventPublisher events;
    private final InvoicePdfRenderer pdfRenderer;

    public BillingService(BillingJdbcRepository repository, SequenceCounterRepository sequences, AuditRecorder audit,
                          InvoiceLotAllocator lotAllocator, StockConsumptionService stock,
                          CommissionService commissions, ApplicationEventPublisher events,
                          InvoicePdfRenderer pdfRenderer) {
        this.repository = repository;
        this.sequences = sequences;
        this.audit = audit;
        this.lotAllocator = lotAllocator;
        this.stock = stock;
        this.commissions = commissions;
        this.events = events;
        this.pdfRenderer = pdfRenderer;
    }

    @Transactional
    public InvoiceResponse create(CreateInvoiceRequest request) {
        TenantContext.ContextValues ctx = requireClinic();
        if (!repository.clientVisible(request.clientId())) {
            throw new EntityNotFoundException("Müşteri bulunamadı.");
        }
        UUID id = UUID.randomUUID();
        String placeholder = DRAFT_NUMBER_PREFIX + id.toString().replace("-", "");
        repository.insertInvoice(id, ctx.organizationId(), ctx.clinicId(), request.inventoryLocationId(),
                request.clientId(), request.animalId(), request.encounterId(), placeholder, request.dueAt(),
                blankToNull(request.notes()), ctx.userId());
        request.lines().forEach(l -> insertLine(ctx.organizationId(), id, l));
        audit.record("INVOICE_CREATE", "INVOICE", id, ctx.clinicId());
        return get(id);
    }

    @Transactional
    public InvoiceResponse addLine(UUID id, LineRequest line) {
        TenantContext.ContextValues ctx = requireClinic();
        lockInvoice(id);
        insertLine(ctx.organizationId(), id, line);
        return get(id);
    }

    /** Taslağın kalemlerini topluca değiştirir (FG-G1, taslak düzenleyicisi). */
    @Transactional
    public InvoiceResponse replaceLines(UUID id, List<LineRequest> lines) {
        TenantContext.ContextValues ctx = requireClinic();
        requireDraft(lockInvoice(id), "Yalnızca taslak faturanın kalemleri değiştirilebilir.");
        repository.deleteLines(id);
        lines.forEach(l -> insertLine(ctx.organizationId(), id, l));
        audit.record("INVOICE_LINES_REPLACE", "INVOICE", id, ctx.clinicId());
        return get(id);
    }

    @Transactional(readOnly = true)
    public PageResponse<InvoiceSummary> list(UUID clinicId, UUID clientId, List<String> status, LocalDate from, LocalDate to,
                                             int page, int size) {
        Pageable pageable = PaginationUtils.createPageable(page, size);
        long total = repository.count(clinicId, clientId, status, from, to);
        List<InvoiceSummary> items = repository.search(clinicId, clientId, status, from, to, pageable.getPageSize(),
                pageable.getOffset());
        Page<InvoiceSummary> result = new PageImpl<>(items, pageable, total);
        return PageResponse.from(result);
    }

    @Transactional(readOnly = true)
    public InvoiceResponse get(UUID id) {
        InvoiceSummary s = repository.findSummary(id).orElseThrow(() -> new EntityNotFoundException(NOT_FOUND));
        BigDecimal[] totals = repository.totals(id);
        BigDecimal[] grossAndDiscount = repository.grossAndDiscount(id);
        InvoiceHeader header = repository.header(id);
        return new InvoiceResponse(s, totals[0], totals[1], header.notes(), header.encounterId(), repository.lines(id),
                repository.payments(id), grossAndDiscount[0], grossAndDiscount[1], header.commissionPolicy());
    }

    /**
     * DRAFT → ISSUED (FG-G2, FG-G3, FG-K4) tek transaction'da: lotsuz ürün satırları FEFO ile lotlara bölünür,
     * organizasyon sayacından sıradaki numara alınır, komisyon stratejisi seçilir ve tek UPDATE ile fatura kesilir;
     * tetikleyici aynı ifadede stoğu düşer ve komisyonu yazar. Yetersiz stokta 409 döner ve hiçbir şey kalmaz
     * (satır bölmesi, sayaç artışı, stok hareketi, komisyon). Sayaç satırı commit'e kadar kilitli olduğundan
     * numaralar boşluksuz ve sıralıdır.
     */
    @Transactional
    public InvoiceResponse issue(UUID id) {
        TenantContext.ContextValues ctx = requireClinic();
        InvoiceLock inv = lockInvoice(id);
        requireDraft(inv, "Yalnızca taslak fatura kesilebilir.");
        lotAllocator.allocate(id, inv.clinicId());
        UUID location = repository.hasStockItems(id) ? stock.defaultLocationId(inv.clinicId()) : null;
        Map<UUID, String> productNames = repository.productNames(id);
        CommissionPolicy policy = commissions.currentPolicy();
        String number = INVOICE_NUMBER_FORMAT.formatted(sequences.next(ctx.organizationId(), INVOICE_COUNTER));
        try {
            repository.issue(id, number, policy.code(), location);
        } catch (DataAccessException ex) {
            throw translateStockGuard(ex, inv.clinicId(), productNames);
        }
        audit.record("INVOICE_ISSUE", "INVOICE", id, ctx.clinicId());
        events.publishEvent(new InvoiceIssuedEvent(ctx.organizationId(), id, inv.clientId(), inv.clinicId()));
        return get(id);
    }

    /** İptal yalnız taslakta (kesilmiş fatura için iade/alacak dekontu gerekir; DB de engeller). */
    @Transactional
    public InvoiceResponse voidInvoice(UUID id) {
        TenantContext.ContextValues ctx = requireClinic();
        InvoiceLock inv = lockInvoice(id);
        requireDraft(inv, "Yalnızca taslak fatura iptal edilebilir.");
        repository.updateStatus(id, "VOID");
        audit.record("INVOICE_VOID", "INVOICE", id, ctx.clinicId());
        return get(id);
    }

    @Transactional
    public InvoiceResponse pay(UUID id, PaymentRequest request) {
        TenantContext.ContextValues ctx = requireClinic();
        InvoiceLock inv = lockInvoice(id);
        if (!PAYABLE.contains(inv.status())) {
            throw new BusinessRuleException("Bu fatura durumunda (%s) ödeme alınamaz.".formatted(inv.status()));
        }
        BigDecimal amount = request.amount();
        BigDecimal grand = repository.grandTotal(id);
        BigDecimal remaining = grand.subtract(repository.paidTotal(id));
        if (amount.compareTo(remaining) > 0) {
            throw new BusinessRuleException("Ödeme tutarı kalan borcu (%s) aşamaz.".formatted(remaining.toPlainString()));
        }
        UUID paymentId = UUID.randomUUID();
        String paymentNo = "P-%06d".formatted(sequences.next(ctx.organizationId(), "PAYMENT"));
        repository.insertPayment(paymentId, ctx.organizationId(), inv.clientId(), ctx.clinicId(), paymentNo,
                request.paymentMethod(), amount, repository.currency(id), blankToNull(request.referenceNo()),
                blankToNull(request.notes()), ctx.userId());
        repository.applyPayment(ctx.organizationId(), id, paymentId, amount);

        String next = amount.compareTo(remaining) == 0 ? "PAID" : "PARTIALLY_PAID";
        if (!next.equals(inv.status())) {
            repository.updateStatus(id, next);
        }
        audit.record("PAYMENT_RECEIVE", "PAYMENT", paymentId, ctx.clinicId());
        return get(id);
    }

    /** FG-G5: fatura / fiş taslağı PDF'i (Türkçe karakterli). */
    @Transactional(readOnly = true)
    public InvoicePdf pdf(UUID id) {
        InvoiceResponse invoice = get(id);
        byte[] content = pdfRenderer.render(invoice, repository.pdfParty(id));
        String status = invoice.invoice().status();
        String name = DRAFT.equals(status) || "VOID".equals(status) ? "taslak" : invoice.invoice().invoiceNo();
        return new InvoicePdf("fatura-" + name + ".pdf", content);
    }

    public record InvoicePdf(String fileName, byte[] content) {
    }

    @Transactional(readOnly = true)
    public ClientBalance balance(UUID clientId) {
        if (!repository.clientVisible(clientId)) {
            throw new EntityNotFoundException("Müşteri bulunamadı.");
        }
        List<InvoiceSummary> open = repository.openInvoices(clientId);
        BigDecimal invoiced = open.stream().map(InvoiceSummary::grandTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal paid = open.stream().map(InvoiceSummary::paidTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ClientBalance(clientId, invoiced, paid, invoiced.subtract(paid), open);
    }

    @Transactional(readOnly = true)
    public DailyCashSummary dailySummary(LocalDate date, UUID clinicId) {
        LocalDate day = date == null ? LocalDate.now(ZoneId.of(ZONE)) : date;
        List<MethodTotal> byMethod = repository.collectedByMethod(day, ZONE, clinicId);
        BigDecimal collected = byMethod.stream().map(MethodTotal::total).reduce(BigDecimal.ZERO, BigDecimal::add);
        Object[] issued = repository.issuedOn(day, ZONE, clinicId);
        return new DailyCashSummary(day, clinicId, byMethod, collected, (Long) issued[0], (BigDecimal) issued[1],
                repository.outstanding(clinicId));
    }

    /** Satır fiyatlaması LinePricing ile doğrulanır (indirim brütü aşamaz); tutarlar DB'de aynı formülle üretilir. */
    private void insertLine(UUID organizationId, UUID invoiceId, LineRequest line) {
        LinePricing.PricedLine priced = LinePricing.price(line.quantity(), line.unitPrice(), line.discountAmount(),
                line.discountRate(), line.taxRate());
        repository.insertLine(organizationId, invoiceId, line, priced.discount());
    }

    /**
     * Eşzamanlı kesimde son kararı DB stok guard'ı verir (23514): FEFO planı yapıldıktan sonra başka bir kesim aynı
     * stoğu almışsa bu fatura 409 INSUFFICIENT_STOCK ile reddedilir ve transaction geri alınır.
     */
    private static RuntimeException translateStockGuard(DataAccessException ex, UUID clinicId,
                                                         Map<UUID, String> productNames) {
        Throwable cause = ex.getMostSpecificCause();
        if (!(cause instanceof SQLException sql) || !STOCK_GUARD_SQLSTATE.equals(sql.getSQLState())
                || sql.getMessage() == null) {
            return ex;
        }
        Matcher m = GUARD_PRODUCT.matcher(sql.getMessage());
        if (!m.find()) {
            return ex;
        }
        UUID productId = UUID.fromString(m.group(1));
        String name = productNames.getOrDefault(productId, "ürün");
        return new ConflictException("Yetersiz stok: %s. Stok sıfırın altına inemez.".formatted(name),
                "INSUFFICIENT_STOCK", Map.of("clinicId", clinicId, "productId", productId));
    }

    private InvoiceLock lockInvoice(UUID id) {
        return repository.lock(id).orElseThrow(() -> new EntityNotFoundException(NOT_FOUND));
    }

    private static void requireDraft(InvoiceLock inv, String message) {
        if (!DRAFT.equals(inv.status())) {
            throw new BusinessRuleException(message);
        }
    }

    private static TenantContext.ContextValues requireClinic() {
        TenantContext.ContextValues ctx = TenantContext.require();
        if (ctx.clinicId() == null) {
            throw new BusinessRuleException("Bu işlem için şube (X-Clinic-Id) gereklidir.");
        }
        return ctx;
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
