package org.codeflow.veterineryproject.audit.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.codeflow.veterineryproject.audit.model.AccessAuditEntry;
import org.codeflow.veterineryproject.audit.service.AccessAuditWriter;
import org.codeflow.veterineryproject.common.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Kritik kayıt görüntülemelerini denetim izine yazar (FG-J4, FOG-13 erişimin kayıt altına alınması).
 * İstek başarıyla tamamlandıktan sonra (2xx) çalışır; yetkisiz/bulunamayan (403/404) görüntüleme kaydedilmez.
 * Denetim kaydı yazılamazsa yanıt zaten gönderilmiş olduğundan hata loglanır, istek etkilenmez.
 */
@Component
public class AccessAuditInterceptor implements HandlerInterceptor {

    static final String REQUEST_ID_HEADER = "X-Request-Id";
    /** audit_logs.request_id varchar(100); user agent yalnız tanımlama için kısaltılır. */
    private static final int MAX_REQUEST_ID_LENGTH = 100;
    private static final int MAX_USER_AGENT_LENGTH = 500;
    private static final Logger log = LoggerFactory.getLogger(AccessAuditInterceptor.class);

    private final AccessAuditWriter writer;

    public AccessAuditInterceptor(AccessAuditWriter writer) {
        this.writer = writer;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        if (ex != null || !HttpMethod.GET.matches(request.getMethod()) || !isSuccess(response.getStatus())) {
            return;
        }
        TenantContext.ContextValues ctx = TenantContext.get().orElse(null);
        if (ctx == null || ctx.organizationId() == null || ctx.userId() == null) {
            return;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        AccessAuditRule.match(path).ifPresent(m -> {
            try {
                writer.write(new AccessAuditEntry(ctx.organizationId(), ctx.clinicId(), ctx.userId(),
                        m.rule().action(), m.rule().entityType(), m.entityId(),
                        truncate(request.getHeader(REQUEST_ID_HEADER), MAX_REQUEST_ID_LENGTH), request.getRemoteAddr(),
                        truncate(request.getHeader("User-Agent"), MAX_USER_AGENT_LENGTH)));
            } catch (RuntimeException failure) {
                log.error("Görüntüleme denetim kaydı yazılamadı: {} {}", m.rule().action(), m.entityId(), failure);
            }
        });
    }

    private static boolean isSuccess(int status) {
        return status >= 200 && status < 300;
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
