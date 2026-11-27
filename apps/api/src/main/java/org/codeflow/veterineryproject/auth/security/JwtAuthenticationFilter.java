package org.codeflow.veterineryproject.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.codeflow.veterineryproject.auth.service.JwtTokenService;
import org.codeflow.veterineryproject.common.tenant.TenantContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String CLINIC_HEADER = "X-Clinic-Id";

    private final JwtTokenService jwtTokenService;

    public JwtAuthenticationFilter(JwtTokenService jwtTokenService) {
        this.jwtTokenService = jwtTokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (isPublicEndpoint(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        JwtTokenService.AccessTokenPayload payload;
        UUID activeClinicId;
        try {
            String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (authorization == null || !authorization.startsWith("Bearer ")) {
                throw new BadCredentialsException("Kimlik doğrulama başarısız.");
            }

            String token = authorization.substring("Bearer ".length()).trim();
            payload = jwtTokenService.parseAccessToken(token);
            activeClinicId = resolveActiveClinicId(request.getHeader(CLINIC_HEADER), payload.clinicIds());
        } catch (AuthenticationException ex) {
            writeError(request, response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", ex.getMessage());
            return;
        } catch (AccessDeniedException ex) {
            writeError(request, response, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN", ex.getMessage());
            return;
        }
        if (payload.mustChangePassword() && !isAllowedWhilePasswordChangeRequired(request)) {
            writeError(request, response, HttpServletResponse.SC_FORBIDDEN, "PASSWORD_CHANGE_REQUIRED",
                    "İlk girişte şifrenizi değiştirmeniz gerekir.");
            return;
        }
        TenantContext.set(payload.organizationId(), payload.userId(), activeClinicId);
        try {
            List<GrantedAuthority> authorities = payload.permissions().stream()
                    .map(SimpleGrantedAuthority::new)
                    .map(GrantedAuthority.class::cast)
                    .toList();

            JwtAuthenticatedUser principal = new JwtAuthenticatedUser(
                    payload.userId(),
                    payload.organizationId(),
                    activeClinicId,
                    payload.clinicIds(),
                    payload.permissions(),
                    payload.jti(),
                    payload.expiresAt()
            );
            UsernamePasswordAuthenticationToken authenticationToken =
                    new UsernamePasswordAuthenticationToken(principal, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authenticationToken);
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
            TenantContext.clear();
        }
    }

    private UUID resolveActiveClinicId(String clinicHeader, List<UUID> clinicIds) {
        if (clinicIds == null || clinicIds.isEmpty()) {
            throw new AccessDeniedException("Klinik erişimi bulunamadı.");
        }

        if (clinicHeader == null || clinicHeader.isBlank()) {
            return clinicIds.get(0);
        }

        UUID requestedClinicId;
        try {
            requestedClinicId = UUID.fromString(clinicHeader.trim());
        } catch (IllegalArgumentException ex) {
            throw new AccessDeniedException("Geçersiz klinik kimliği.");
        }

        if (!clinicIds.contains(requestedClinicId)) {
            throw new AccessDeniedException("Bu klinik için yetkiniz yok.");
        }
        return requestedClinicId;
    }

    private void writeError(HttpServletRequest request, HttpServletResponse response, int status, String code, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        String body = "{\"timestamp\":\"" + org.codeflow.veterineryproject.common.time.ApiDateTime.format(Instant.now()) + "\",\"status\":" + status
                + ",\"code\":\"" + code + "\",\"message\":\"" + escape(message)
                + "\",\"path\":\"" + escape(request.getRequestURI()) + "\"}";
        response.getWriter().write(body);
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private boolean isAllowedWhilePasswordChangeRequired(HttpServletRequest request) {
        String path = request.getRequestURI();
        return "/api/auth/change-password".equals(path) || "/api/auth/me".equals(path) || "/api/auth/logout".equals(path);
    }

    private boolean isPublicEndpoint(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null) {
            return false;
        }

        return path.startsWith("/api/auth/login")
                || path.equals("/api/auth/sign-in")
                || path.equals("/api/auth/organizations")
                || path.startsWith("/api/auth/refresh")
                || path.equals("/api/auth/signup")
                || path.equals("/api/auth/signup/plans")
                || path.equals("/api/auth/invitations/preview")
                || path.equals("/api/auth/accept-invite")
                || path.startsWith("/api/platform/auth/")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui")
                || path.equals("/swagger-ui.html")
                || path.equals("/actuator/health")
                || HttpMethod.OPTIONS.matches(request.getMethod());
    }
}
