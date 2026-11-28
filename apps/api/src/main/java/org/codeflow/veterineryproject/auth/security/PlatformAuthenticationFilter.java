package org.codeflow.veterineryproject.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.codeflow.veterineryproject.auth.service.JwtTokenService;
import org.codeflow.veterineryproject.common.tenant.TenantContext;
import org.codeflow.veterineryproject.common.time.ApiDateTime;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

/**
 * /api/platform/** uçlarında yalnız platform token'ını (typ = PLATFORM_ACCESS) kabul eder. Personel veya sahip
 * token'ı 401 alır. Bağlam {@link TenantContext#setPlatformAdmin} ile kurulur: transaction'larda
 * app.platform_admin_id set edilir, organizasyon bağlamı olmadığından tenant politikaları satır göstermez; erişim
 * yalnız V4/V29 platform politikalarıyla olur ve yazmalar V6 log'una düşer.
 * <p>
 * Bean değildir (servlet filtresi olarak otomatik kaydedilmesin); yalnız platform güvenlik zincirine eklenir.
 */
public class PlatformAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTH_PATH_PREFIX = "/api/platform/auth/";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenService jwtTokenService;

    public PlatformAuthenticationFilter(JwtTokenService jwtTokenService) {
        this.jwtTokenService = jwtTokenService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || path.startsWith(AUTH_PATH_PREFIX) || HttpMethod.OPTIONS.matches(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        JwtTokenService.PlatformTokenPayload payload;
        try {
            String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
                writeUnauthorized(request, response);
                return;
            }
            payload = jwtTokenService.parsePlatformToken(authorization.substring(BEARER_PREFIX.length()).trim());
        } catch (AuthenticationException ex) {
            writeUnauthorized(request, response);
            return;
        }

        TenantContext.setPlatformAdmin(payload.adminId());
        try {
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    new PlatformAdminPrincipal(payload.adminId(), payload.email()), null,
                    List.of(new SimpleGrantedAuthority(PlatformAdminPrincipal.AUTHORITY)));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
            TenantContext.clear();
        }
    }

    private static void writeUnauthorized(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"timestamp\":\"" + ApiDateTime.format(Instant.now())
                + "\",\"status\":401,\"code\":\"UNAUTHORIZED\",\"message\":\"Kimlik doğrulama başarısız.\",\"path\":\""
                + request.getRequestURI().replace("\\", "\\\\").replace("\"", "\\\"") + "\"}");
    }
}
