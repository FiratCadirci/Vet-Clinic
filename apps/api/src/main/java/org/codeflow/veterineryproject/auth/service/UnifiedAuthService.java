package org.codeflow.veterineryproject.auth.service;

import org.codeflow.veterineryproject.auth.dto.AuthLoginCandidate;
import org.codeflow.veterineryproject.auth.dto.AuthLoginResponse;
import org.codeflow.veterineryproject.auth.dto.AuthPanel;
import org.codeflow.veterineryproject.auth.dto.AuthRoleSummary;
import org.codeflow.veterineryproject.auth.dto.PlatformAuthResponse;
import org.codeflow.veterineryproject.auth.dto.PlatformLoginRequest;
import org.codeflow.veterineryproject.auth.dto.UnifiedLoginRequest;
import org.codeflow.veterineryproject.auth.dto.UnifiedLoginResponse;
import org.codeflow.veterineryproject.auth.repository.AuthReadRepository;
import org.codeflow.veterineryproject.common.api.ConflictException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class UnifiedAuthService {

    private static final String INVALID_CREDENTIALS_MESSAGE = "Kimlik bilgileri hatalı.";

    private final PlatformAuthService platformAuthService;
    private final AuthService authService;
    private final AuthReadRepository authReadRepository;

    public UnifiedAuthService(
            PlatformAuthService platformAuthService,
            AuthService authService,
            AuthReadRepository authReadRepository
    ) {
        this.platformAuthService = platformAuthService;
        this.authService = authService;
        this.authReadRepository = authReadRepository;
    }

    public List<org.codeflow.veterineryproject.auth.dto.OrganizationOption> listOrganizations() {
        return authReadRepository.listActiveOrganizations();
    }

    public UnifiedLoginResponse signIn(UnifiedLoginRequest request) {
        String email = request.email().trim();
        boolean orgSpecified = request.organizationCode() != null;

        if (!orgSpecified) {
            try {
                PlatformAuthResponse platform = platformAuthService.login(new PlatformLoginRequest(email, request.password()));
                return new UnifiedLoginResponse(
                        UnifiedLoginResponse.ACCOUNT_PLATFORM,
                        AuthPanel.PLATFORM_ADMIN,
                        List.of(AuthPanel.PLATFORM_ADMIN),
                        null,
                        platform
                );
            } catch (BadCredentialsException ignored) {
                // platform yöneticisi değil veya parola uyuşmadı; kiracı kullanıcısı olarak denenir
            }
        }

        List<AuthLoginCandidate> candidates = authReadRepository.resolveLoginCandidates(
                email, orgSpecified ? request.organizationCode() : null);
        if (candidates.isEmpty()) {
            throw new BadCredentialsException(INVALID_CREDENTIALS_MESSAGE);
        }

        AuthLoginCandidate chosen;
        AuthLoginResponse tenant;
        if (candidates.size() == 1) {
            chosen = candidates.get(0);
            tenant = authService.loginResolved(chosen.organizationId(), chosen.userId(), request.password());
        } else {
            List<AuthLoginCandidate> matches = candidates.stream()
                    .filter(c -> authService.credentialsMatch(c.organizationId(), c.userId(), request.password()))
                    .toList();
            if (matches.isEmpty()) {
                throw new BadCredentialsException(INVALID_CREDENTIALS_MESSAGE);
            }
            if (matches.size() > 1) {
                throw ambiguous(matches);
            }
            chosen = matches.get(0);
            tenant = authService.loginResolved(chosen.organizationId(), chosen.userId(), request.password());
        }

        List<AuthPanel> panels = resolvePanels(tenant.roles(), chosen.client());
        return new UnifiedLoginResponse(
                UnifiedLoginResponse.ACCOUNT_TENANT,
                panels.get(0),
                panels,
                tenant,
                null
        );
    }

    static List<AuthPanel> resolvePanels(List<AuthRoleSummary> roles, boolean client) {
        Set<AuthPanel> panels = new LinkedHashSet<>();
        Set<String> codes = new LinkedHashSet<>();
        if (roles != null) {
            roles.forEach(r -> codes.add(r.code() == null ? "" : r.code().toLowerCase(Locale.ROOT)));
        }
        if (codes.contains("clinic_admin")) {
            panels.add(AuthPanel.CLINIC_ADMIN);
        }
        if (codes.contains("veterinarian")) {
            panels.add(AuthPanel.VETERINARIAN);
        }
        if (codes.contains("receptionist")) {
            panels.add(AuthPanel.RECEPTION);
        }
        if (client) {
            panels.add(AuthPanel.PET_OWNER);
        }
        if (panels.isEmpty()) {
            panels.add(AuthPanel.UNASSIGNED);
        }
        return new ArrayList<>(panels);
    }

    private ConflictException ambiguous(List<AuthLoginCandidate> matches) {
        List<Map<String, Object>> organizations = new ArrayList<>();
        for (AuthLoginCandidate c : matches) {
            Map<String, Object> org = new LinkedHashMap<>();
            org.put("id", c.organizationId());
            org.put("code", c.organizationCode());
            org.put("name", c.organizationName());
            organizations.add(org);
        }
        return new ConflictException(
                "Bu hesap birden fazla organizasyonda kayıtlı; organizationCode belirtin.",
                "AMBIGUOUS_ORGANIZATION",
                Map.of("organizations", organizations)
        );
    }
}
