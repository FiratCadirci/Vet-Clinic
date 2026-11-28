package org.codeflow.veterineryproject.audit.model;

import org.codeflow.veterineryproject.common.time.ApiDateTime;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;

/**
 * Denetim izi filtresi (FG-L2): işlem, kayıt türü, kayıt, kullanıcı, zaman aralığı (from dahil, to hariç) ve
 * en fazla {@value #MAX_LIMIT} kayıt (FOG-8). Geçersiz değerler 400 olur.
 */
public record AuditLogFilter(String action, String entityType, UUID entityId, UUID userId, OffsetDateTime from,
                             OffsetDateTime to, int limit) {

    public static final int DEFAULT_LIMIT = 100;
    public static final int MAX_LIMIT = 500;

    public AuditLogFilter {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("Kayıt sayısı 1 ile " + MAX_LIMIT + " arasında olmalıdır.");
        }
        if (from != null && to != null && !from.isBefore(to)) {
            throw new IllegalArgumentException("Başlangıç tarihi bitiş tarihinden önce olmalıdır.");
        }
    }

    /** İstek parametrelerinden filtre; işlem ve kayıt türü büyük/küçük harf duyarsızdır. */
    public static AuditLogFilter of(String action, String entityType, String entityId, String userId, String from,
                                    String to, Integer limit) {
        return new AuditLogFilter(upper(action), upper(entityType), uuid(entityId, "kayıt kimliği"),
                uuid(userId, "kullanıcı kimliği"), time(from), time(to), limit == null ? DEFAULT_LIMIT : limit);
    }

    private static String upper(String text) {
        return text == null || text.isBlank() ? null : text.trim().toUpperCase(Locale.ROOT);
    }

    private static UUID uuid(String text, String field) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(text.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Geçersiz " + field + ".");
        }
    }

    private static OffsetDateTime time(String text) {
        return text == null || text.isBlank() ? null : ApiDateTime.parseOffset(text);
    }
}
