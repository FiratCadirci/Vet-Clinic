package org.codeflow.veterineryproject.commission.controller;

import org.codeflow.veterineryproject.auth.security.Permission;
import org.codeflow.veterineryproject.commission.dto.CommissionDto.CommissionRecordResponse;
import org.codeflow.veterineryproject.commission.service.CommissionService;
import org.codeflow.veterineryproject.common.pagination.PageResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/** Kliniğin kendi komisyon kayıtları (salt okunur). Kural yönetimi platform tarafındadır. */
@RestController
@RequestMapping("/api/v1/commissions")
@PreAuthorize("hasAuthority('" + Permission.COMMISSIONS_READ + "')")
public class CommissionController {

    private final CommissionService service;

    public CommissionController(CommissionService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<CommissionRecordResponse> list(@RequestParam(required = false) UUID clinicId,
                                                       @RequestParam(required = false) UUID invoiceId,
                                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                       @RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "50") int size) {
        return service.list(clinicId, invoiceId, from, to, page, size);
    }
}
