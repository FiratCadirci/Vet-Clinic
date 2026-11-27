package org.codeflow.veterineryproject.user.dto;

import java.util.List;

/** Yetki matrisi: satırlar yetkiler (kod + Türkçe açıklama), sütunlar roller (her rolün yetki kodlarıyla). */
public record PermissionMatrixResponse(List<PermissionItem> permissions, List<RoleResponse> roles) {

    public record PermissionItem(String code, String description) {
    }
}
