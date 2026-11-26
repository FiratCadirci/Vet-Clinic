package org.codeflow.veterineryproject.animal.controller;

import jakarta.validation.Valid;
import org.codeflow.veterineryproject.animal.dto.AnimalHealthDto.AllergyRequest;
import org.codeflow.veterineryproject.animal.dto.AnimalHealthDto.AllergyResponse;
import org.codeflow.veterineryproject.animal.dto.AnimalHealthDto.HealthSummary;
import org.codeflow.veterineryproject.animal.dto.AnimalHealthDto.IdentifierRequest;
import org.codeflow.veterineryproject.animal.dto.AnimalHealthDto.IdentifierResponse;
import org.codeflow.veterineryproject.animal.dto.AnimalHealthDto.WeightRequest;
import org.codeflow.veterineryproject.animal.dto.AnimalHealthDto.WeightResponse;
import org.codeflow.veterineryproject.animal.service.AnimalHealthService;
import org.codeflow.veterineryproject.auth.security.Permission;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Hasta kartı: kilo geçmişi, alerjiler ve kimlik (mikroçip vb.) numaraları. */
@RestController
@RequestMapping("/api/v1/animals/{animalId}")
public class AnimalHealthController {

    private static final String READ = "hasAuthority('" + Permission.CLIENTS_READ + "')";
    private static final String WRITE = "hasAuthority('" + Permission.CLIENTS_WRITE + "')";

    private final AnimalHealthService service;

    public AnimalHealthController(AnimalHealthService service) {
        this.service = service;
    }

    @GetMapping("/health-summary")
    @PreAuthorize(READ)
    public HealthSummary summary(@PathVariable UUID animalId) {
        return service.summary(animalId);
    }

    @GetMapping("/weights")
    @PreAuthorize(READ)
    public List<WeightResponse> weights(@PathVariable UUID animalId) {
        return service.weights(animalId);
    }

    @PostMapping("/weights")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(WRITE)
    public WeightResponse addWeight(@PathVariable UUID animalId, @Valid @RequestBody WeightRequest request) {
        return service.addWeight(animalId, request);
    }

    @GetMapping("/allergies")
    @PreAuthorize(READ)
    public List<AllergyResponse> allergies(@PathVariable UUID animalId,
                                           @RequestParam(defaultValue = "false") boolean activeOnly) {
        return service.allergies(animalId, activeOnly);
    }

    @PostMapping("/allergies")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(WRITE)
    public AllergyResponse addAllergy(@PathVariable UUID animalId, @Valid @RequestBody AllergyRequest request) {
        return service.addAllergy(animalId, request);
    }

    @PostMapping("/allergies/{allergyId}/resolve")
    @PreAuthorize(WRITE)
    public AllergyResponse resolveAllergy(@PathVariable UUID animalId, @PathVariable UUID allergyId) {
        return service.resolveAllergy(animalId, allergyId);
    }

    @GetMapping("/identifiers")
    @PreAuthorize(READ)
    public List<IdentifierResponse> identifiers(@PathVariable UUID animalId) {
        return service.identifiers(animalId);
    }

    @PostMapping("/identifiers")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(WRITE)
    public IdentifierResponse addIdentifier(@PathVariable UUID animalId, @Valid @RequestBody IdentifierRequest request) {
        return service.addIdentifier(animalId, request);
    }
}
