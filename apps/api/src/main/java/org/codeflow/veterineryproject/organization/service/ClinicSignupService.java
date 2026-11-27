package org.codeflow.veterineryproject.organization.service;

import org.codeflow.veterineryproject.auth.dto.AuthLoginResponse;
import org.codeflow.veterineryproject.auth.service.AuthService;
import org.codeflow.veterineryproject.organization.dto.OrganizationDto.Registration;
import org.codeflow.veterineryproject.organization.dto.OrganizationDto.SignupRequest;
import org.codeflow.veterineryproject.organization.repository.OrganizationJdbcRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Locale;

/**
 * FG-A1 klinik kaydı (Facade): organizasyon, klinik grubu, ilk şube, sistem rolleri, aşı şablonu taslakları,
 * klinik yöneticisi ve 14 günlük deneme aboneliği DB'de tek işlemde oluşur; ardından yönetici için oturum açılır.
 */
@Service
public class ClinicSignupService {

    /** Proje dokümanı: kayıtla 14 günlük deneme aboneliği başlar. */
    public static final int TRIAL_DAYS = 14;
    private static final String DEFAULT_BRANCH_NAME = "Merkez Şube";

    private final OrganizationJdbcRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;
    private final TransactionTemplate transactionTemplate;

    public ClinicSignupService(OrganizationJdbcRepository repository, PasswordEncoder passwordEncoder,
                               AuthService authService, TransactionTemplate transactionTemplate) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.authService = authService;
        this.transactionTemplate = transactionTemplate;
    }

    public AuthLoginResponse signup(SignupRequest request) {
        String branchName = blankToNull(request.branchName());
        Registration registration = transactionTemplate.execute(status -> repository.registerClinic(
                request.clinicName().trim(),
                blankToNull(request.phone()),
                lower(request.email()),
                blankToNull(request.address()),
                blankToNull(request.taxNumber()),
                branchName == null ? DEFAULT_BRANCH_NAME : branchName,
                request.adminFullName().trim(),
                lower(request.adminEmail()),
                passwordEncoder.encode(request.password()),
                blankToNull(request.planCode()),
                TRIAL_DAYS));
        if (registration == null || !"OK".equals(registration.result())) {
            throw new IllegalArgumentException("Abonelik paketi bulunamadı.");
        }
        // Kayıt commit edildikten sonra normal giriş akışıyla access + refresh token üretilir.
        return authService.loginResolved(registration.organizationId(), registration.userId(), request.password());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String lower(String value) {
        String trimmed = blankToNull(value);
        return trimmed == null ? null : trimmed.toLowerCase(Locale.ROOT);
    }
}
