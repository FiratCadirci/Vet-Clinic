package org.codeflow.veterineryproject.auth.dto;

import java.util.List;

public record UnifiedLoginResponse(
        String accountType,
        AuthPanel panel,
        List<AuthPanel> availablePanels,
        AuthLoginResponse tenant,
        PlatformAuthResponse platform
) {
    public static final String ACCOUNT_TENANT = "TENANT";
    public static final String ACCOUNT_PLATFORM = "PLATFORM";
}
