package org.codeflow.veterineryproject.common.tenant;

import org.springframework.core.NamedThreadLocal;

import java.util.Optional;
import java.util.UUID;

public final class TenantContext {

    private static final ThreadLocal<ContextValues> CONTEXT = new NamedThreadLocal<>("tenant-context");

    private TenantContext() {
    }

    public static void set(UUID organizationId, UUID userId, UUID clinicId) {
        CONTEXT.set(new ContextValues(organizationId, userId, clinicId, null));
    }

    public static void setPlatformAdmin(UUID platformAdminId) {
        CONTEXT.set(new ContextValues(null, null, null, platformAdminId));
    }

    public static Optional<ContextValues> get() {
        return Optional.ofNullable(CONTEXT.get());
    }

    public static ContextValues require() {
        ContextValues values = CONTEXT.get();
        if (values == null || values.organizationId() == null || values.userId() == null) {
            throw new org.springframework.security.authentication.BadCredentialsException("Kimlik doğrulama başarısız.");
        }
        return values;
    }

    public static void clear() {
        CONTEXT.remove();
    }

    public static Scope withTenant(UUID organizationId, UUID userId, UUID clinicId) {
        ContextValues previous = CONTEXT.get();
        set(organizationId, userId, clinicId);
        return () -> {
            if (previous == null) {
                clear();
            } else {
                CONTEXT.set(previous);
            }
        };
    }

    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }

    public record ContextValues(UUID organizationId, UUID userId, UUID clinicId, UUID platformAdminId) {
    }
}

