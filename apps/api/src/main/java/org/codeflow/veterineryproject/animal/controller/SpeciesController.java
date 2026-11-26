package org.codeflow.veterineryproject.animal.controller;

import org.codeflow.veterineryproject.animal.dto.BreedResponse;
import org.codeflow.veterineryproject.animal.dto.SpeciesResponse;
import org.codeflow.veterineryproject.animal.service.SpeciesService;
import org.codeflow.veterineryproject.auth.security.Permission;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/species")
@PreAuthorize("hasAuthority('" + Permission.CLIENTS_READ + "')")
public class SpeciesController {

    private final SpeciesService speciesService;

    public SpeciesController(SpeciesService speciesService) {
        this.speciesService = speciesService;
    }

    @GetMapping
    public List<SpeciesResponse> list() {
        return speciesService.listSpecies();
    }

    @GetMapping("/{id}/breeds")
    public List<BreedResponse> breeds(@PathVariable UUID id) {
        return speciesService.listBreeds(id);
    }
}
