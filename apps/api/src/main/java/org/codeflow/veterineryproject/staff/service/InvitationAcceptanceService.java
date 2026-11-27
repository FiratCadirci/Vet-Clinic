package org.codeflow.veterineryproject.staff.service;

import jakarta.persistence.EntityNotFoundException;
import org.codeflow.veterineryproject.auth.dto.AuthLoginResponse;
import org.codeflow.veterineryproject.auth.service.AuthService;
import org.codeflow.veterineryproject.common.api.BusinessRuleException;
import org.codeflow.veterineryproject.common.api.ConflictException;
import org.codeflow.veterineryproject.staff.dto.StaffInvitationDto.AcceptRequest;
import org.codeflow.veterineryproject.staff.dto.StaffInvitationDto.PreviewResponse;
import org.codeflow.veterineryproject.staff.repository.StaffInvitationRepository;
import org.codeflow.veterineryproject.staff.repository.StaffInvitationRepository.AcceptResult;
import org.codeflow.veterineryproject.staff.repository.StaffInvitationRepository.PreviewRow;
import org.codeflow.veterineryproject.subscription.service.PlanLimitDecision;
import org.codeflow.veterineryproject.subscription.service.PlanResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;

/**
 * Oturum öncesi davet önizleme ve kabulü. Kabul DB'de tek işlemdir (vet.accept_staff_invitation): organizasyon satırı
 * kilitlenir, kullanıcı limiti sayılır, kullanıcı ve şube rol atamaları oluşur, davet tüketilir. Sonra oturum açılır.
 */
@Service
public class InvitationAcceptanceService {

    private static final String NOT_FOUND = "Davet bulunamadı.";

    private final StaffInvitationRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;
    private final TransactionTemplate transactionTemplate;

    public InvitationAcceptanceService(StaffInvitationRepository repository, PasswordEncoder passwordEncoder,
                                       AuthService authService, TransactionTemplate transactionTemplate) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.authService = authService;
        this.transactionTemplate = transactionTemplate;
    }

    public PreviewResponse preview(String rawToken) {
        InvitationToken token = new InvitationToken(rawToken);
        PreviewRow row = transactionTemplate.execute(status -> repository.preview(token.hash()).orElse(null));
        if (row == null) {
            throw new EntityNotFoundException(NOT_FOUND);
        }
        InvitationStatus.valueOf(row.status()).requirePending();
        return new PreviewResponse(row.organizationName(), row.email(), row.roleCode(), row.roleName(), row.expiresAt());
    }

    public AuthLoginResponse accept(AcceptRequest request) {
        InvitationToken token = new InvitationToken(request.token());
        String passwordHash = passwordEncoder.encode(request.password());
        AcceptResult result = transactionTemplate.execute(status ->
                repository.accept(token.hash(), request.fullName().trim(), passwordHash));
        if (result == null) {
            throw new EntityNotFoundException(NOT_FOUND);
        }
        switch (result.result()) {
            case "OK" -> {
                // Kullanıcı commit edildi; normal giriş akışıyla access + refresh token üretilir.
                return authService.loginResolved(result.organizationId(), result.userId(), request.password());
            }
            case "NOT_FOUND" -> throw new EntityNotFoundException(NOT_FOUND);
            case "REVOKED" -> throw InvitationStatus.REVOKED.conflict();
            case "ACCEPTED" -> throw InvitationStatus.ACCEPTED.conflict();
            case "EXPIRED" -> throw InvitationStatus.EXPIRED.conflict();
            case "EMAIL_TAKEN" -> throw new ConflictException("Bu e-posta adresi ile kayıtlı bir kullanıcı zaten var.",
                    "EMAIL_TAKEN", Map.of());
            case "NO_ACTIVE_CLINIC" -> throw new BusinessRuleException("Davetteki şubeler artık aktif değil; yeni davet isteyin.");
            case "USER_LIMIT" -> throw PlanLimitDecision.toException(PlanResource.USERS, null, result.maxAllowed(), null);
            case "SUBSCRIPTION_INACTIVE" -> throw PlanLimitDecision.toException(PlanResource.USERS,
                    PlanLimitDecision.SUBSCRIPTION_INACTIVE, null, null);
            default -> throw new IllegalStateException("Beklenmeyen davet kabul sonucu: " + result.result());
        }
    }
}
