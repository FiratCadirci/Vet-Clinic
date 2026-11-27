package org.codeflow.veterineryproject.staff.controller;

import jakarta.validation.Valid;
import org.codeflow.veterineryproject.auth.security.Permission;
import org.codeflow.veterineryproject.staff.dto.StaffInvitationDto.CreateRequest;
import org.codeflow.veterineryproject.staff.dto.StaffInvitationDto.InvitationResponse;
import org.codeflow.veterineryproject.staff.service.StaffInvitationService;
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

import java.util.List;
import java.util.UUID;

/** Personel davetleri (users.manage). Davet bağlantısı e-postayla gider; yanıtlarda davet kodu bulunmaz. */
@RestController
@RequestMapping("/api/v1/staff-invitations")
@PreAuthorize("hasAuthority('" + Permission.USERS_MANAGE + "')")
public class StaffInvitationController {

    private final StaffInvitationService service;

    public StaffInvitationController(StaffInvitationService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InvitationResponse create(@RequestHeader("X-Clinic-Id") String clinicId, @Valid @RequestBody CreateRequest request) {
        return service.create(request);
    }

    /** status: PENDING, ACCEPTED, REVOKED veya EXPIRED (boşsa tümü). */
    @GetMapping
    public List<InvitationResponse> list(@RequestParam(required = false) String status) {
        return service.list(status);
    }

    @PostMapping("/{id}/revoke")
    public InvitationResponse revoke(@RequestHeader("X-Clinic-Id") String clinicId, @PathVariable UUID id) {
        return service.revoke(id);
    }
}
