package org.codeflow.veterineryproject.search.controller;

import org.codeflow.veterineryproject.auth.security.Permission;
import org.codeflow.veterineryproject.search.dto.GlobalSearchResponse;
import org.codeflow.veterineryproject.search.service.GlobalSearchService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/search")
public class GlobalSearchController {

    private final GlobalSearchService service;

    public GlobalSearchController(GlobalSearchService service) {
        this.service = service;
    }

    /** Müşteri adı/telefonu, hayvan adı, hasta no ve çip no ile tek kutudan arama. */
    @GetMapping
    @PreAuthorize("hasAuthority('" + Permission.CLIENTS_READ + "')")
    public GlobalSearchResponse search(@RequestParam String q) {
        return service.search(q);
    }
}
