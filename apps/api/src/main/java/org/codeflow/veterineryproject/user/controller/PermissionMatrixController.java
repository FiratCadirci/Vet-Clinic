package org.codeflow.veterineryproject.user.controller;

import org.codeflow.veterineryproject.user.dto.PermissionMatrixResponse;
import org.codeflow.veterineryproject.user.service.PermissionMatrixService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Personel yetki matrisi (FG-J3); oturum açmış her personel görüntüleyebilir. */
@RestController
@RequestMapping("/api/v1/permission-matrix")
@PreAuthorize("isAuthenticated()")
public class PermissionMatrixController {

    private final PermissionMatrixService service;

    public PermissionMatrixController(PermissionMatrixService service) {
        this.service = service;
    }

    @GetMapping
    public PermissionMatrixResponse matrix() {
        return service.matrix();
    }
}
