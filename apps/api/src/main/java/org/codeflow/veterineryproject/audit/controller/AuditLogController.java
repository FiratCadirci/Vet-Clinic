package org.codeflow.veterineryproject.audit.controller;

import org.codeflow.veterineryproject.audit.dto.AuditLogResponse;
import org.codeflow.veterineryproject.audit.model.AuditLogFilter;
import org.codeflow.veterineryproject.audit.service.AuditLogQueryService;
import org.codeflow.veterineryproject.auth.security.JwtAuthenticatedUser;
import org.codeflow.veterineryproject.auth.security.Permission;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Denetim izi (FG-L2), audit.read. Filtreler: action, entityType, entityId, userId, from/to (from dahil, to hariç),
 * limit (varsayılan 100, en fazla 500). Sonuç en yeni kayıttan başlar.
 */
@RestController
@RequestMapping("/api/v1/audit-logs")
public class AuditLogController {

    private final AuditLogQueryService audit;

    public AuditLogController(AuditLogQueryService audit) {
        this.audit = audit;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + Permission.AUDIT_READ + "')")
    public List<AuditLogResponse> search(@AuthenticationPrincipal JwtAuthenticatedUser principal,
                                         @RequestParam(required = false) String action,
                                         @RequestParam(required = false) String entityType,
                                         @RequestParam(required = false) String entityId,
                                         @RequestParam(required = false) String userId,
                                         @RequestParam(required = false) String from,
                                         @RequestParam(required = false) String to,
                                         @RequestParam(required = false) Integer limit) {
        return audit.search(AuditLogFilter.of(action, entityType, entityId, userId, from, to, limit),
                principal.clinicIds());
    }
}
