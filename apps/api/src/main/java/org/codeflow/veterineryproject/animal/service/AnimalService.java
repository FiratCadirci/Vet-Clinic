package org.codeflow.veterineryproject.animal.service;

import jakarta.persistence.EntityNotFoundException;
import org.codeflow.veterineryproject.animal.dto.AnimalOwnerRequest;
import org.codeflow.veterineryproject.animal.dto.AnimalOwnerResponse;
import org.codeflow.veterineryproject.animal.dto.AnimalResponse;
import org.codeflow.veterineryproject.animal.dto.CreateAnimalRequest;
import org.codeflow.veterineryproject.animal.dto.DeceasedRequest;
import org.codeflow.veterineryproject.animal.dto.UpdateAnimalRequest;
import org.codeflow.veterineryproject.animal.repository.AnimalJdbcRepository;
import org.codeflow.veterineryproject.common.api.BusinessRuleException;
import org.codeflow.veterineryproject.common.api.ConflictException;
import org.codeflow.veterineryproject.common.pagination.PageResponse;
import org.codeflow.veterineryproject.common.tenant.TenantContext;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class AnimalService {

    private static final String NOT_FOUND = "Hasta bulunamadı.";
    private static final String CLIENT_NOT_FOUND = "Hayvan sahibi bulunamadı.";

    private final AnimalJdbcRepository repository;

    public AnimalService(AnimalJdbcRepository repository) {
        this.repository = repository;
    }

    /** animals ve animal_owners tek transaction'da yazılır; patient_no organizasyon sayacından üretilir. */
    @Transactional
    public AnimalResponse create(CreateAnimalRequest request) {
        UUID organizationId = TenantContext.require().organizationId();

        if (!repository.speciesActive(request.speciesId())) {
            throw new BusinessRuleException("Tür bulunamadı.");
        }
        validateBreed(request.breedId(), request.speciesId());
        validateOwners(request.owners());

        boolean sterilized = Boolean.TRUE.equals(request.sterilized()) || request.sterilizationDate() != null;
        validateDates(request.birthDate(), request.sterilizationDate());

        UUID id = UUID.randomUUID();
        String patientNo = "P-%06d".formatted(repository.nextPatientSequence(organizationId));
        AnimalResponse values = new AnimalResponse(id, patientNo, request.name().trim(), request.speciesId(), null,
                request.breedId(), null, request.sex() == null ? "UNKNOWN" : request.sex(), request.birthDate(),
                Boolean.TRUE.equals(request.birthDateEstimated()), blankToNull(request.color()),
                blankToNull(request.distinguishingMarks()), sterilized, request.sterilizationDate(), null,
                blankToNull(request.profileNotes()), null, null, List.of());
        try {
            repository.insert(id, organizationId, patientNo, values);
        } catch (DuplicateKeyException ex) {
            throw new ConflictException("Hasta numarası zaten kullanımda: " + patientNo);
        }
        for (AnimalOwnerRequest owner : request.owners()) {
            repository.insertOwner(organizationId, id, owner.clientId(), relationship(owner),
                    Boolean.TRUE.equals(owner.isPrimaryContact()), legalOwner(owner));
        }
        return load(id);
    }

    @Transactional(readOnly = true)
    public PageResponse<AnimalResponse> list(String query, UUID speciesId, Pageable pageable) {
        return page(query, speciesId, null, pageable);
    }

    @Transactional(readOnly = true)
    public PageResponse<AnimalResponse> listByClient(UUID clientId, Pageable pageable) {
        if (repository.clientActive(clientId).isEmpty()) {
            throw new EntityNotFoundException(CLIENT_NOT_FOUND);
        }
        return page(null, null, clientId, pageable);
    }

    @Transactional(readOnly = true)
    public AnimalResponse get(UUID id) {
        return load(id);
    }

    @Transactional(readOnly = true)
    public List<AnimalResponse> findByChip(String chipNo) {
        String value = chipNo.replaceAll("[\\s-]", "");
        List<AnimalResponse> animals = repository.findByIdentifier(value);
        if (animals.isEmpty()) {
            throw new EntityNotFoundException("Bu çip/kimlik numarasıyla kayıtlı hasta bulunamadı.");
        }
        Map<UUID, List<AnimalOwnerResponse>> owners = repository.findOwners(animals.stream().map(AnimalResponse::id).toList());
        return animals.stream().map(a -> a.withOwners(owners.getOrDefault(a.id(), List.of()))).toList();
    }

    @Transactional
    public AnimalResponse update(UUID id, UpdateAnimalRequest request) {
        TenantContext.require();
        AnimalResponse current = repository.findById(id).orElseThrow(() -> new EntityNotFoundException(NOT_FOUND));

        UUID breedId = request.breedId() != null ? request.breedId() : current.breedId();
        if (request.breedId() != null) {
            validateBreed(breedId, current.speciesId());
        }
        LocalDate birthDate = request.birthDate() != null ? request.birthDate() : current.birthDate();
        LocalDate sterilizationDate = request.sterilizationDate() != null ? request.sterilizationDate() : current.sterilizationDate();
        validateDates(birthDate, sterilizationDate);
        if (request.name() != null && request.name().isBlank()) {
            throw new IllegalArgumentException("Hasta adı boş olamaz.");
        }

        boolean sterilized = request.sterilized() != null ? request.sterilized() : current.sterilized();
        if (request.sterilizationDate() != null) {
            sterilized = true;
        }
        repository.update(id, new AnimalResponse(id, current.patientNo(),
                request.name() != null ? request.name().trim() : current.name(),
                current.speciesId(), null, breedId, null,
                request.sex() != null ? request.sex() : current.sex(), birthDate,
                request.birthDateEstimated() != null ? request.birthDateEstimated() : current.birthDateEstimated(),
                request.color() != null ? blankToNull(request.color()) : current.color(),
                request.distinguishingMarks() != null ? blankToNull(request.distinguishingMarks()) : current.distinguishingMarks(),
                sterilized, sterilizationDate, current.deceasedAt(),
                request.profileNotes() != null ? blankToNull(request.profileNotes()) : current.profileNotes(),
                null, null, List.of()));
        return load(id);
    }

    @Transactional
    public AnimalResponse addOwner(UUID animalId, AnimalOwnerRequest request) {
        UUID organizationId = TenantContext.require().organizationId();
        load(animalId);
        requireActiveClient(request.clientId());
        if (repository.hasCurrentOwner(animalId, request.clientId())) {
            throw new ConflictException("Bu müşteri hastanın zaten güncel sahibi.");
        }
        boolean primary = Boolean.TRUE.equals(request.isPrimaryContact());
        if (primary) {
            repository.clearPrimaryContact(animalId);
        }
        repository.insertOwner(organizationId, animalId, request.clientId(), relationship(request), primary, legalOwner(request));
        return load(animalId);
    }

    @Transactional
    public AnimalResponse makePrimaryContact(UUID animalId, UUID ownerId) {
        TenantContext.require();
        load(animalId);
        repository.findCurrentOwner(animalId, ownerId).orElseThrow(() -> new EntityNotFoundException("Sahiplik kaydı bulunamadı."));
        repository.clearPrimaryContact(animalId);
        repository.setPrimaryContact(ownerId);
        return load(animalId);
    }

    @Transactional
    public AnimalResponse endOwner(UUID animalId, UUID ownerId) {
        TenantContext.require();
        load(animalId);
        AnimalOwnerResponse owner = repository.findCurrentOwner(animalId, ownerId)
                .orElseThrow(() -> new EntityNotFoundException("Sahiplik kaydı bulunamadı."));
        if (repository.countCurrentOwners(animalId) <= 1) {
            throw new BusinessRuleException("Hastanın en az bir güncel sahibi olmalı.");
        }
        if (owner.primaryContact()) {
            throw new BusinessRuleException("Birincil iletişim sahibi sonlandırılamaz; önce başka bir sahibi birincil iletişim yapın.");
        }
        repository.endOwner(ownerId);
        return load(animalId);
    }

    @Transactional
    public AnimalResponse markDeceased(UUID id, DeceasedRequest request) {
        TenantContext.require();
        AnimalResponse animal = load(id);
        if (animal.deceasedAt() != null) {
            throw new ConflictException("Hasta zaten vefat etmiş olarak işaretli.");
        }
        LocalDate deceasedAt = request.deceasedAt();
        if (deceasedAt.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Vefat tarihi gelecekte olamaz.");
        }
        if (animal.birthDate() != null && deceasedAt.isBefore(animal.birthDate())) {
            throw new IllegalArgumentException("Vefat tarihi doğum tarihinden önce olamaz.");
        }
        repository.markDeceased(id, deceasedAt);
        return load(id);
    }

    private PageResponse<AnimalResponse> page(String query, UUID speciesId, UUID clientId, Pageable pageable) {
        List<AnimalResponse> animals = repository.search(query, speciesId, clientId, pageable.getPageSize(), pageable.getOffset());
        Map<UUID, List<AnimalOwnerResponse>> owners = repository.findOwners(animals.stream().map(AnimalResponse::id).toList());
        List<AnimalResponse> content = animals.stream().map(a -> a.withOwners(owners.getOrDefault(a.id(), List.of()))).toList();
        return PageResponse.from(new PageImpl<>(content, pageable, repository.count(query, speciesId, clientId)));
    }

    private AnimalResponse load(UUID id) {
        AnimalResponse animal = repository.findById(id).orElseThrow(() -> new EntityNotFoundException(NOT_FOUND));
        return animal.withOwners(repository.findOwners(List.of(id)).getOrDefault(id, List.of()));
    }

    private void validateBreed(UUID breedId, UUID speciesId) {
        if (breedId != null && !repository.breedBelongsToSpecies(breedId, speciesId)) {
            throw new BusinessRuleException("Irk, seçilen türe ait değil.");
        }
    }

    private void validateDates(LocalDate birthDate, LocalDate sterilizationDate) {
        if (birthDate != null && birthDate.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Doğum tarihi gelecekte olamaz.");
        }
        if (sterilizationDate != null && sterilizationDate.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Kısırlaştırma tarihi gelecekte olamaz.");
        }
        if (birthDate != null && sterilizationDate != null && sterilizationDate.isBefore(birthDate)) {
            throw new IllegalArgumentException("Kısırlaştırma tarihi doğum tarihinden önce olamaz.");
        }
    }

    private void validateOwners(List<AnimalOwnerRequest> owners) {
        Set<UUID> seen = new HashSet<>();
        long primaryCount = 0;
        for (AnimalOwnerRequest owner : owners) {
            if (!seen.add(owner.clientId())) {
                throw new IllegalArgumentException("Aynı müşteri birden fazla kez eklenemez.");
            }
            if (Boolean.TRUE.equals(owner.isPrimaryContact())) {
                primaryCount++;
            }
        }
        if (primaryCount != 1) {
            throw new IllegalArgumentException("Tam olarak bir sahip birincil iletişim olmalıdır.");
        }
        owners.forEach(owner -> requireActiveClient(owner.clientId()));
    }

    private void requireActiveClient(UUID clientId) {
        Boolean active = repository.clientActive(clientId).orElseThrow(() -> new BusinessRuleException(CLIENT_NOT_FOUND));
        if (!active) {
            throw new BusinessRuleException("Pasif hayvan sahibi hastaya bağlanamaz.");
        }
    }

    private String relationship(AnimalOwnerRequest owner) {
        return owner.relationshipType() == null ? "OWNER" : owner.relationshipType();
    }

    private boolean legalOwner(AnimalOwnerRequest owner) {
        return owner.isLegalOwner() == null || owner.isLegalOwner();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
