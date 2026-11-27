package org.codeflow.veterineryproject.staff.controller;

import jakarta.validation.Valid;
import org.codeflow.veterineryproject.auth.dto.AuthLoginResponse;
import org.codeflow.veterineryproject.staff.dto.StaffInvitationDto.AcceptRequest;
import org.codeflow.veterineryproject.staff.dto.StaffInvitationDto.PreviewRequest;
import org.codeflow.veterineryproject.staff.dto.StaffInvitationDto.PreviewResponse;
import org.codeflow.veterineryproject.staff.service.InvitationAcceptanceService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Davet bağlantısı (${WEB_URL}/accept-invite?token=...) için oturum öncesi uçlar. Token URL yolunda değil gövdede
 * gönderilir (erişim loglarına düşmesin).
 */
@RestController
@RequestMapping("/api/auth")
public class InvitationAcceptanceController {

    private final InvitationAcceptanceService service;

    public InvitationAcceptanceController(InvitationAcceptanceService service) {
        this.service = service;
    }

    @PostMapping("/invitations/preview")
    public PreviewResponse preview(@Valid @RequestBody PreviewRequest request) {
        return service.preview(request.token());
    }

    @PostMapping("/accept-invite")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthLoginResponse accept(@Valid @RequestBody AcceptRequest request) {
        return service.accept(request);
    }
}
