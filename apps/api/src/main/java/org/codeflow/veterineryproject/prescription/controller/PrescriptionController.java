package org.codeflow.veterineryproject.prescription.controller;

import jakarta.validation.Valid;
import org.codeflow.veterineryproject.auth.security.Permission;
import org.codeflow.veterineryproject.prescription.dto.PrescriptionItemRequest;
import org.codeflow.veterineryproject.prescription.dto.PrescriptionItemResponse;
import org.codeflow.veterineryproject.prescription.service.PrescriptionService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/encounters/{encounterId}/prescriptions")
public class PrescriptionController {

    private static final String READ = "hasAuthority('" + Permission.CLINICAL_READ + "')";
    private static final String WRITE = "hasAuthority('" + Permission.CLINICAL_WRITE + "')";

    private final PrescriptionService service;

    public PrescriptionController(PrescriptionService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(READ)
    public List<PrescriptionItemResponse> list(@PathVariable UUID encounterId) {
        return service.list(encounterId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(WRITE)
    public PrescriptionItemResponse add(@RequestHeader("X-Clinic-Id") String clinicId, @PathVariable UUID encounterId,
                                        @Valid @RequestBody PrescriptionItemRequest request) {
        return service.add(encounterId, request);
    }

    /** Açık muayenede yazım hatası düzeltmesi: reçete kalemini kaldırmak yazma yetkisiyle yapılır. */
    @DeleteMapping("/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(WRITE)
    public void delete(@RequestHeader("X-Clinic-Id") String clinicId, @PathVariable UUID encounterId,
                       @PathVariable UUID itemId) {
        service.delete(encounterId, itemId);
    }
}
