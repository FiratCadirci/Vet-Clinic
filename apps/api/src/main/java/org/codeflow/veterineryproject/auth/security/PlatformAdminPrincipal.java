package org.codeflow.veterineryproject.auth.security;

import java.util.UUID;

/** Platform yöneticisi oturumu (PLATFORM_ACCESS token'ı). Kiracı, şube ve personel yetkisi taşımaz. */
public record PlatformAdminPrincipal(UUID adminId, String email) {

    /** Platform uçlarının tek yetkisi; personel token'ındaki izin kodlarıyla çakışmaz. */
    public static final String AUTHORITY = "PLATFORM_ADMIN";
}
