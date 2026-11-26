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
import org.codeflow.veterineryproject.billing.repository.BillingJdbcRepository;
import org.codeflow.veterineryproject.billing.repository.BillingJdbcRepository.InvoiceLock;
import org.codeflow.veterineryproject.common.api.BusinessRuleException;
import org.codeflow.veterineryproject.common.audit.AuditRecorder;
import org.codeflow.veterineryproject.common.pagination.PageResponse;
import org.codeflow.veterineryproject.common.pagination.PaginationUtils;
import org.codeflow.veterineryproject.common.sequence.SequenceCounterRepository;
import org.codeflow.veterineryproject.common.tenant.TenantContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class BillingService {

    static final String ZONE = "Europe/Istanbul";
    private static final Set<String> PAYABLE = Set.of("ISSUED", "PARTIALLY_PAID", "OVERDUE");
    private static final String NOT_FOUND = "Fatura bulunamadı.";

    private final BillingJdbcRepository repository;
    private final SequenceCounterRepository sequences;
    private final AuditRecorder audit;

    public BillingService(BillingJdbcRepository repository, SequenceCounterRepository sequences, AuditRecorder audit) {
        this.repository = repository;
        this.sequences = sequences;
        this.audit = audit;
    }

    @Transactional
    public InvoiceResponse create(CreateInvoiceRequest request) {
        TenantContext.ContextValues ctx = requireClinic();
        if (!repository.clientVisible(request.clientId())) {
            throw new EntityNotFoundException("Müşteri bulunamadı.");
        }
        UUID id = UUID.randomUUID();
        String number = "F-%06d".formatted(sequences.next(ctx.organizationId(), "INVOICE"));
        repository.insertInvoice(id, ctx.organizationId(), ctx.clinicId(), request.inventoryLocationId(),
                request.clientId(), request.animalId(), request.encounterId(), number, request.dueAt(),
                blankToNull(request.notes()), ctx.userId());
        request.lines().forEach(l -> repository.insertLine(ctx.organizationId(), id, l));
        audit.record("INVOICE_CREATE", "INVOICE", id, ctx.clinicId());
        return get(id);
    }

    @Transactional
    public InvoiceResponse addLine(UUID id, LineRequest line) {
        TenantContext.ContextValues ctx = requireClinic();
        lockInvoice(id);
        repository.insertLine(ctx.organizationId(), id, line);
        return get(id);
    }

    @Transactional(readOnly = true)
    public PageResponse<InvoiceSummary> list(UUID clinicId, UUID clientId, String status, LocalDate from, LocalDate to,
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
        return new InvoiceResponse(s, totals[0], totals[1], null, null, repository.lines(id), repository.payments(id));
    }

    /** DRAFT -> ISSUED: stok düşümü ve komisyon DB tetikleyicisi tarafından yapılır. */
    @Transactional
    public InvoiceResponse issue(UUID id) {
        TenantContext.ContextValues ctx = requireClinic();
        lockInvoice(id);
        repository.updateStatus(id, "ISSUED");
        audit.record("INVOICE_ISSUE", "INVOICE", id, ctx.clinicId());
        return get(id);
    }

    @Transactional
    public InvoiceResponse voidInvoice(UUID id) {
        TenantContext.ContextValues ctx = requireClinic();
        InvoiceLock inv = lockInvoice(id);
        if (!"DRAFT".equals(inv.status())) {
            throw new BusinessRuleException("Yalnızca taslak fatura iptal edilebilir.");
        }
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

    private InvoiceLock lockInvoice(UUID id) {
        return repository.lock(id).orElseThrow(() -> new EntityNotFoundException(NOT_FOUND));
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
