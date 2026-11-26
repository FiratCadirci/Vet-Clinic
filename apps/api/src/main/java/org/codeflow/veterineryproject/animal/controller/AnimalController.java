package org.codeflow.veterineryproject.animal.controller;

import jakarta.validation.Valid;
import org.codeflow.veterineryproject.animal.dto.AnimalOwnerRequest;
import org.codeflow.veterineryproject.animal.dto.AnimalResponse;
import org.codeflow.veterineryproject.animal.dto.CreateAnimalRequest;
import org.codeflow.veterineryproject.animal.dto.CreateClientAnimalRequest;
import org.codeflow.veterineryproject.animal.dto.DeceasedRequest;
import org.codeflow.veterineryproject.animal.dto.UpdateAnimalRequest;
import org.codeflow.veterineryproject.animal.service.AnimalService;
import org.codeflow.veterineryproject.auth.security.Permission;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class AnimalController {

    private static final String READ = "hasAuthority('" + Permission.CLIENTS_READ + "')";
    private static final String WRITE = "hasAuthority('" + Permission.CLIENTS_WRITE + "')";

    private final AnimalService animalService;

    public AnimalController(AnimalService animalService) {
        this.animalService = animalService;
    }

    /** Kayıt, X-Clinic-Id ile belirtilen şubeye açılır; başlık zorunludur. */
    @PostMapping("/animals")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(WRITE)
    public AnimalResponse create(
            @RequestHeader("X-Clinic-Id") String clinicId,
            @Valid @RequestBody CreateAnimalRequest request
    ) {
        return animalService.create(request);
    }

    @GetMapping("/animals")
    @PreAuthorize(READ)
    public PageResponse<AnimalResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID speciesId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return animalService.list(q, speciesId, PaginationUtils.createPageable(page, size));
    }

    /** Çip/kimlik numarasından hayvan(lar) ve sahipleri; bulunamazsa 404. */
    @GetMapping("/animals/by-chip/{chipNo}")
    @PreAuthorize(READ)
    public List<AnimalResponse> byChip(@PathVariable String chipNo) {
        return animalService.findByChip(chipNo);
    }

    @GetMapping("/animals/{id}")
    @PreAuthorize(READ)
    public AnimalResponse get(@PathVariable UUID id) {
        return animalService.get(id);
    }

    /** Hayvanı oluşturur ve path'teki müşteriyi birincil sahip olarak tek transaction'da bağlar. */
    @PostMapping("/clients/{clientId}/animals")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(WRITE)
    public AnimalResponse createForClient(
            @RequestHeader("X-Clinic-Id") String clinicId,
            @PathVariable UUID clientId,
            @Valid @RequestBody CreateClientAnimalRequest request
    ) {
        return animalService.create(request.toCreateAnimalRequest(clientId));
    }

    @GetMapping("/clients/{clientId}/animals")
    @PreAuthorize(READ)
    public PageResponse<AnimalResponse> listByClient(
            @PathVariable UUID clientId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return animalService.listByClient(clientId, PaginationUtils.createPageable(page, size));
    }

    @PatchMapping("/animals/{id}")
    @PreAuthorize(WRITE)
    public AnimalResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateAnimalRequest request) {
        return animalService.update(id, request);
    }

    @PostMapping("/animals/{id}/owners")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(WRITE)
    public AnimalResponse addOwner(@PathVariable UUID id, @Valid @RequestBody AnimalOwnerRequest request) {
        return animalService.addOwner(id, request);
    }

    @PostMapping("/animals/{id}/owners/{ownerId}/primary")
    @PreAuthorize(WRITE)
    public AnimalResponse makePrimaryContact(@PathVariable UUID id, @PathVariable UUID ownerId) {
        return animalService.makePrimaryContact(id, ownerId);
    }

    @PostMapping("/animals/{id}/owners/{ownerId}/end")
    @PreAuthorize(WRITE)
    public AnimalResponse endOwner(@PathVariable UUID id, @PathVariable UUID ownerId) {
        return animalService.endOwner(id, ownerId);
    }

    @PostMapping("/animals/{id}/deceased")
    @PreAuthorize(WRITE)
    public AnimalResponse markDeceased(@PathVariable UUID id, @Valid @RequestBody DeceasedRequest request) {
        return animalService.markDeceased(id, request);
    }
}
