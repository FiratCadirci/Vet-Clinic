package org.codeflow.veterineryproject.billing.controller;

import jakarta.validation.Valid;
import org.codeflow.veterineryproject.auth.security.Permission;
import org.codeflow.veterineryproject.billing.dto.BillingDto.ClientBalance;
import org.codeflow.veterineryproject.billing.dto.BillingDto.CreateInvoiceRequest;
import org.codeflow.veterineryproject.billing.dto.BillingDto.DailyCashSummary;
import org.codeflow.veterineryproject.billing.dto.BillingDto.InvoiceResponse;
import org.codeflow.veterineryproject.billing.dto.BillingDto.InvoiceSummary;
import org.codeflow.veterineryproject.billing.dto.BillingDto.LineRequest;
import org.codeflow.veterineryproject.billing.dto.BillingDto.PaymentRequest;
import org.codeflow.veterineryproject.billing.service.BillingService;
import org.codeflow.veterineryproject.common.pagination.PageResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/** Fatura, tahsilat, müşteri bakiyesi ve günlük kasa özeti. Yazma uçlarında X-Clinic-Id zorunludur. */
@RestController
@RequestMapping("/api/v1/billing")
@PreAuthorize("hasAuthority('" + Permission.BILLING_MANAGE + "')")
public class BillingController {

    private final BillingService service;

    public BillingController(BillingService service) {
        this.service = service;
    }

    @GetMapping("/invoices")
    public PageResponse<InvoiceSummary> list(@RequestParam(required = false) UUID clinicId,
                                             @RequestParam(required = false) UUID clientId,
                                             @RequestParam(required = false) String status,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return service.list(clinicId, clientId, status, from, to, page, size);
    }

    @GetMapping("/invoices/{id}")
    public InvoiceResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping("/invoices")
    @ResponseStatus(HttpStatus.CREATED)
    public InvoiceResponse create(@RequestHeader("X-Clinic-Id") String clinicId,
                                  @Valid @RequestBody CreateInvoiceRequest request) {
        return service.create(request);
    }

    @PostMapping("/invoices/{id}/lines")
    @ResponseStatus(HttpStatus.CREATED)
    public InvoiceResponse addLine(@RequestHeader("X-Clinic-Id") String clinicId, @PathVariable UUID id,
                                   @Valid @RequestBody LineRequest request) {
        return service.addLine(id, request);
    }

    @PostMapping("/invoices/{id}/issue")
    public InvoiceResponse issue(@RequestHeader("X-Clinic-Id") String clinicId, @PathVariable UUID id) {
        return service.issue(id);
    }

    @PostMapping("/invoices/{id}/void")
    public InvoiceResponse voidInvoice(@RequestHeader("X-Clinic-Id") String clinicId, @PathVariable UUID id) {
        return service.voidInvoice(id);
    }

    @PostMapping("/invoices/{id}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public InvoiceResponse pay(@RequestHeader("X-Clinic-Id") String clinicId, @PathVariable UUID id,
                               @Valid @RequestBody PaymentRequest request) {
        return service.pay(id, request);
    }

    @GetMapping("/clients/{clientId}/balance")
    public ClientBalance balance(@PathVariable UUID clientId) {
        return service.balance(clientId);
    }

    @GetMapping("/cashier/daily")
    public DailyCashSummary daily(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                  @RequestParam(required = false) UUID clinicId) {
        return service.dailySummary(date, clinicId);
    }
}
