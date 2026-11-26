package org.codeflow.veterineryproject.common.api;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.time.Instant;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrityViolation(DataIntegrityViolationException ex, HttpServletRequest request) {
        String sqlState = extractSqlState(ex);
        if ("23P01".equals(sqlState)) {
            return response(HttpStatus.CONFLICT, "APPOINTMENT_TIME_CONFLICT",
                    "Bu saat dolu. Lütfen farklı bir saat seçin.", request, Map.of("sqlState", sqlState));
        }
        if ("23505".equals(sqlState) || "23503".equals(sqlState)) {
            return response(HttpStatus.CONFLICT, "DB_CONFLICT", "Veri çakışması oluştu.", request, Map.of("sqlState", sqlState));
        }
        if ("23514".equals(sqlState) || "P0001".equals(sqlState)) {
            return databaseRuleViolation(ex, sqlState, request);
        }
        if ("42501".equals(sqlState)) {
            return response(HttpStatus.FORBIDDEN, "FORBIDDEN", "Bu işlem için yetkiniz yok.", request, Map.of("sqlState", sqlState));
        }
        return response(HttpStatus.BAD_REQUEST, "DB_ERROR", "Veritabanı işlemi başarısız.", request, Map.of("sqlState", sqlState));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiError> handleDataAccess(DataAccessException ex, HttpServletRequest request) {
        String sqlState = extractSqlState(ex);
        if ("23P01".equals(sqlState)) {
            return response(HttpStatus.CONFLICT, "APPOINTMENT_TIME_CONFLICT",
                    "Bu saat dolu. Lütfen farklı bir saat seçin.", request, Map.of("sqlState", sqlState));
        }
        if ("23505".equals(sqlState) || "23503".equals(sqlState)) {
            return response(HttpStatus.CONFLICT, "DB_CONFLICT", "Veri çakışması oluştu.", request, Map.of("sqlState", sqlState));
        }
        if ("42501".equals(sqlState)) {
            return response(HttpStatus.FORBIDDEN, "FORBIDDEN", "Bu işlem için yetkiniz yok.", request, Map.of("sqlState", sqlState));
        }
        if ("23514".equals(sqlState) || "P0001".equals(sqlState)) {
            return databaseRuleViolation(ex, sqlState, request);
        }
        return response(HttpStatus.BAD_REQUEST, "DB_ACCESS_ERROR", "Veritabanı erişim hatası.", request, Map.of("sqlState", sqlState));
    }

    private ResponseEntity<ApiError> databaseRuleViolation(Exception ex, String sqlState, HttpServletRequest request) {
        String databaseMessage = extractSqlMessage(ex);
        if (databaseMessage != null && databaseMessage.contains("çalışma saatleri dışında")) {
            return response(HttpStatus.UNPROCESSABLE_ENTITY, "VETERINARIAN_OUTSIDE_WORKING_HOURS",
                    "Seçilen saat hekimin çalışma saatleri dışında.", request, Map.of("sqlState", sqlState));
        }
        if (databaseMessage != null && databaseMessage.contains("aynı gün içinde")) {
            return response(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_APPOINTMENT_TIME_RANGE",
                    "Randevu seçilen klinik yerel saatinde aynı gün içinde olmalıdır.", request,
                    Map.of("sqlState", sqlState));
        }
        return response(HttpStatus.UNPROCESSABLE_ENTITY, "DB_RULE_VIOLATION", "İş kuralı ihlali.", request,
                Map.of("sqlState", sqlState));
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleUploadTooLarge(org.springframework.web.multipart.MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return response(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "Dosya boyutu sınırı aşıldı.", request, Map.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return response(HttpStatus.FORBIDDEN, "FORBIDDEN", "Bu işlem için yetkiniz yok.", request, Map.of("reason", ex.getMessage()));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        return response(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Kimlik doğrulama başarısız.", request, Map.of());
    }

    @ExceptionHandler({EntityNotFoundException.class, NoSuchElementException.class})
    public ResponseEntity<ApiError> handleNotFound(RuntimeException ex, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, "NOT_FOUND", "Kayıt bulunamadı.", request, Map.of("reason", ex.getMessage()));
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            ConstraintViolationException.class,
            BindException.class,
            HandlerMethodValidationException.class
    })
    public ResponseEntity<ApiError> handleValidation(Exception ex, HttpServletRequest request) {
        Map<String, Object> details = new LinkedHashMap<>();
        String message = "Geçersiz istek.";
        if (ex instanceof BindException bind && bind.hasFieldErrors()) {
            // Alan bazında Türkçe doğrulama mesajları (spring.web.locale=tr): {"fields": {"email": "geçerli bir e-posta adresi olmalı"}}
            Map<String, String> fields = new LinkedHashMap<>();
            bind.getFieldErrors().forEach(e -> fields.putIfAbsent(e.getField(), e.getDefaultMessage()));
            details.put("fields", fields);
            message = "Geçersiz istek: " + fields.entrySet().stream()
                    .map(e -> e.getKey() + " " + e.getValue())
                    .collect(java.util.stream.Collectors.joining(", "));
        } else {
            details.put("reason", ex.getMessage());
        }
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message, request, details);
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(org.springframework.http.converter.HttpMessageNotReadableException ex, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "İstek gövdesi okunamadı; alan biçimlerini (tarih-saat için yyyy-MM-dd HH:mm) kontrol edin.", request, Map.of());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> handleConflict(ConflictException ex, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, ex.code(), ex.getMessage(), request, ex.details());
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiError> handleBusinessRule(BusinessRuleException ex, HttpServletRequest request) {
        return response(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_VIOLATION", ex.getMessage(), request, Map.of());
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiError> handleMissingHeader(MissingRequestHeaderException ex, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "MISSING_HEADER", ex.getHeaderName() + " başlığı zorunludur.", request,
                Map.of("header", ex.getHeaderName()));
    }

    @ExceptionHandler(java.time.format.DateTimeParseException.class)
    public ResponseEntity<ApiError> handleDateTimeParse(java.time.format.DateTimeParseException ex, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_DATE_TIME",
                "Tarih-saat biçimi geçersiz (yyyy-MM-dd HH:mm veya ISO-8601).", request, Map.of("value", ex.getParsedString()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage(), request, Map.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest request) {
        String sqlState = extractSqlState(ex);
        if ("P0001".equals(sqlState)) {
            return response(HttpStatus.UNPROCESSABLE_ENTITY, "DB_RULE_VIOLATION", "İş kuralı ihlali.", request, Map.of("sqlState", sqlState));
        }
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Beklenmeyen bir hata oluştu.", request, Map.of("reason", ex.getClass().getSimpleName()));
    }

    private ResponseEntity<ApiError> response(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request,
            Map<String, Object> details
    ) {
        ApiError error = new ApiError(
                Instant.now(),
                status.value(),
                code,
                message,
                request.getRequestURI(),
                details
        );
        return ResponseEntity.status(status).body(error);
    }

    private String extractSqlState(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SQLException sqlException) {
                return sqlException.getSQLState();
            }
            current = current.getCause();
        }
        return null;
    }

    private String extractSqlMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SQLException sqlException && sqlException.getMessage() != null) {
                return sqlException.getMessage();
            }
            current = current.getCause();
        }
        return null;
    }
}
