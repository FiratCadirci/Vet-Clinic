package org.codeflow.veterineryproject.user.controller;

import org.codeflow.veterineryproject.auth.security.Permission;
import org.codeflow.veterineryproject.user.dto.RoleResponse;
import org.codeflow.veterineryproject.user.service.RoleService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/roles")
@PreAuthorize("hasAuthority('" + Permission.USERS_MANAGE + "')")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    public List<RoleResponse> list() {
        return roleService.list();
    }
}
