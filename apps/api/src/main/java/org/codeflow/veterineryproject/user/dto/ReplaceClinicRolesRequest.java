package org.codeflow.veterineryproject.user.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** Personelin rolünü ve çalıştığı şubeleri tek işlemde belirler (çağıranın yetki alanındaki şubeler için). */
public record ReplaceClinicRolesRequest(
        @NotNull UUID roleId,
        @NotEmpty @Size(max = 100) List<@NotNull UUID> clinicIds
) {
}
