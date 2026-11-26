package org.codeflow.veterineryproject.user.controller;

import jakarta.validation.Valid;
import org.codeflow.veterineryproject.auth.security.Permission;
import org.codeflow.veterineryproject.common.pagination.PageResponse;
import org.codeflow.veterineryproject.common.pagination.PaginationUtils;
import org.codeflow.veterineryproject.user.dto.ClinicRoleAssignmentRequest;
import org.codeflow.veterineryproject.user.dto.CreateUserRequest;
import org.codeflow.veterineryproject.user.dto.CreateVeterinarianRequest;
import org.codeflow.veterineryproject.user.dto.UpdateUserRequest;
import org.codeflow.veterineryproject.user.dto.UserResponse;
import org.codeflow.veterineryproject.user.dto.VeterinarianResponse;
import org.codeflow.veterineryproject.user.service.UserService;
import org.codeflow.veterineryproject.user.service.VeterinarianService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasAuthority('" + Permission.USERS_MANAGE + "')")
public class UserController {

    private final UserService userService;
    private final VeterinarianService veterinarianService;

    public UserController(UserService userService, VeterinarianService veterinarianService) {
        this.userService = userService;
        this.veterinarianService = veterinarianService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        return userService.create(request);
    }

    @GetMapping
    public PageResponse<UserResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return userService.list(q, active, PaginationUtils.createPageable(page, size));
    }

    @GetMapping("/{id}")
    public UserResponse get(@PathVariable UUID id) {
        return userService.get(id);
    }

    @PatchMapping("/{id}")
    public UserResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request) {
        return userService.update(id, request);
    }

    @PostMapping("/{id}/clinic-roles")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse addClinicRole(@PathVariable UUID id, @Valid @RequestBody ClinicRoleAssignmentRequest request) {
        return userService.addClinicRole(id, request);
    }

    @DeleteMapping("/{id}/clinic-roles/{assignmentId}")
    public UserResponse endClinicRole(@PathVariable UUID id, @PathVariable UUID assignmentId) {
        return userService.endClinicRole(id, assignmentId);
    }

    @PostMapping("/{id}/deactivate")
    public UserResponse deactivate(@PathVariable UUID id) {
        return userService.deactivate(id);
    }

    @PostMapping("/{id}/activate")
    public UserResponse activate(@PathVariable UUID id) {
        return userService.activate(id);
    }

    @PostMapping("/{id}/veterinarian")
    @ResponseStatus(HttpStatus.CREATED)
    public VeterinarianResponse createVeterinarian(@PathVariable UUID id, @Valid @RequestBody CreateVeterinarianRequest request) {
        return veterinarianService.create(id, request);
    }

    @GetMapping("/{id}/veterinarian")
    public VeterinarianResponse getVeterinarian(@PathVariable UUID id) {
        return veterinarianService.get(id);
    }
}
