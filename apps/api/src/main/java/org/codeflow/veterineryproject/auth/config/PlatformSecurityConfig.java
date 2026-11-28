package org.codeflow.veterineryproject.auth.config;

import org.codeflow.veterineryproject.auth.security.PlatformAdminPrincipal;
import org.codeflow.veterineryproject.auth.security.PlatformAuthenticationFilter;
import org.codeflow.veterineryproject.auth.service.JwtTokenService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Platform yöneticisi paneli için ayrı güvenlik zinciri (/api/platform/**). Personel zincirinden önce değerlendirilir;
 * böylece platform ve personel token türleri birbirinin uçlarında kullanılamaz (personel token'ı burada 401,
 * platform token'ı personel uçlarında JwtAuthenticationFilter'da 401).
 */
@Configuration
public class PlatformSecurityConfig {

    private static final String PLATFORM_PATTERN = "/api/platform/**";
    private static final String PLATFORM_AUTH_PATTERN = "/api/platform/auth/**";

    @Bean
    @Order(1)
    public SecurityFilterChain platformSecurityFilterChain(HttpSecurity http, JwtTokenService jwtTokenService) throws Exception {
        http
                .securityMatcher(PLATFORM_PATTERN)
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PLATFORM_AUTH_PATTERN).permitAll()
                        .anyRequest().hasAuthority(PlatformAdminPrincipal.AUTHORITY))
                .addFilterBefore(new PlatformAuthenticationFilter(jwtTokenService), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
