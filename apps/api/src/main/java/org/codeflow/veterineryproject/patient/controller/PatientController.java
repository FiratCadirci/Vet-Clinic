package org.codeflow.veterineryproject.patient.controller;

import org.codeflow.veterineryproject.auth.security.Permission;
import org.codeflow.veterineryproject.common.pagination.PageResponse;
import org.codeflow.veterineryproject.common.pagination.PaginationUtils;
import org.codeflow.veterineryproject.patient.dto.PatientHistoryResponse;
import org.codeflow.veterineryproject.patient.dto.PatientSummaryResponse;
import org.codeflow.veterineryproject.patient.service.PatientHistoryService;
import org.codeflow.veterineryproject.patient.service.PatientQueryService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Personel hasta ekranlarının okuma uçları. Yazma işlemleri /clients ve /animals uçlarındadır. */
@RestController
@RequestMapping("/api/v1/patients")
public class PatientController {

    private final PatientQueryService patients;
    private final PatientHistoryService history;

    public PatientController(PatientQueryService patients, PatientHistoryService history) {
        this.patients = patients;
        this.history = history;
    }

    /** {@code q}: hasta adı, hasta no, sahip adı, sahip telefonu veya çip/kimlik no; {@code ownerId}: sahibin güncel hayvanları. */
    @GetMapping
    @PreAuthorize("hasAuthority('" + Permission.CLIENTS_READ + "')")
    public PageResponse<PatientSummaryResponse> list(@RequestParam(required = false) String q,
                                                     @RequestParam(required = false) UUID ownerId,
                                                     @RequestParam(required = false) Integer page,
                                                     @RequestParam(required = false) Integer size) {
        return patients.list(q, ownerId, PaginationUtils.createPageable(page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permission.CLIENTS_READ + "')")
    public PatientSummaryResponse get(@PathVariable UUID id) {
        return patients.get(id);
    }

    /** Hasta kaydı (clients.read) veya klinik kayıt (clinical.read) okuma yetkisinden biri yeterlidir. */
    @GetMapping("/{id}/history")
    @PreAuthorize("hasAnyAuthority('" + Permission.CLIENTS_READ + "', '" + Permission.CLINICAL_READ + "')")
    public PatientHistoryResponse history(@PathVariable UUID id,
                                          @RequestParam(required = false) Integer page,
                                          @RequestParam(required = false) Integer size,
                                          Authentication authentication) {
        boolean clinical = authentication.getAuthorities().stream()
                .anyMatch(a -> Permission.CLINICAL_READ.equals(a.getAuthority()));
        return history.history(id, clinical, PaginationUtils.createPageable(page, size));
    }
}
