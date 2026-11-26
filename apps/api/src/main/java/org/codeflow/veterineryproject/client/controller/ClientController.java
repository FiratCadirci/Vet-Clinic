package org.codeflow.veterineryproject.client.controller;

import jakarta.validation.Valid;
import org.codeflow.veterineryproject.auth.security.Permission;
import org.codeflow.veterineryproject.client.dto.ClientListItemResponse;
import org.codeflow.veterineryproject.client.dto.ClientResponse;
import org.codeflow.veterineryproject.client.dto.CreateClientRequest;
import org.codeflow.veterineryproject.client.dto.UpdateClientRequest;
import org.codeflow.veterineryproject.client.service.ClientService;
import org.codeflow.veterineryproject.common.pagination.PageResponse;
import org.codeflow.veterineryproject.common.pagination.PaginationUtils;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clients")
public class ClientController {

    private final ClientService clientService;

    public ClientController(ClientService clientService) {
        this.clientService = clientService;
    }

    /** Kayıt, X-Clinic-Id ile belirtilen şubeye açılır; başlık zorunludur. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('" + Permission.CLIENTS_WRITE + "')")
    public ClientResponse create(
            @RequestHeader("X-Clinic-Id") String clinicId,
            @Valid @RequestBody CreateClientRequest request
    ) {
        return clientService.create(request);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + Permission.CLIENTS_READ + "')")
    public PageResponse<ClientListItemResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return clientService.list(q, active, PaginationUtils.createPageable(page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permission.CLIENTS_READ + "')")
    public ClientResponse get(@PathVariable UUID id) {
        return clientService.get(id);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permission.CLIENTS_WRITE + "')")
    public ClientResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateClientRequest request) {
        return clientService.update(id, request);
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('" + Permission.CLIENTS_WRITE + "')")
    public ClientResponse deactivate(@PathVariable UUID id) {
        return clientService.deactivate(id);
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('" + Permission.CLIENTS_WRITE + "')")
    public ClientResponse activate(@PathVariable UUID id) {
        return clientService.activate(id);
    }
}
