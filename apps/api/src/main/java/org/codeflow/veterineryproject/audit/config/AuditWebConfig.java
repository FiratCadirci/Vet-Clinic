package org.codeflow.veterineryproject.audit.config;

import org.codeflow.veterineryproject.audit.web.AccessAuditInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Görüntüleme denetimi yalnız personel API'sinde (/api/v1) çalışır; giriş ve platform uçları kapsam dışıdır. */
@Configuration
public class AuditWebConfig implements WebMvcConfigurer {

    private final AccessAuditInterceptor accessAuditInterceptor;

    public AuditWebConfig(AccessAuditInterceptor accessAuditInterceptor) {
        this.accessAuditInterceptor = accessAuditInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(accessAuditInterceptor).addPathPatterns("/api/v1/**");
    }
}
