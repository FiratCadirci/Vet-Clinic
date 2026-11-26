package org.codeflow.veterineryproject.animal.service;

import jakarta.persistence.EntityNotFoundException;
import org.codeflow.veterineryproject.animal.dto.AnimalHealthDto.AllergyRequest;
import org.codeflow.veterineryproject.animal.dto.AnimalHealthDto.AllergyResponse;
import org.codeflow.veterineryproject.animal.dto.AnimalHealthDto.HealthSummary;
import org.codeflow.veterineryproject.animal.dto.AnimalHealthDto.IdentifierRequest;
import org.codeflow.veterineryproject.animal.dto.AnimalHealthDto.IdentifierResponse;
import org.codeflow.veterineryproject.animal.dto.AnimalHealthDto.WeightRequest;
import org.codeflow.veterineryproject.animal.dto.AnimalHealthDto.WeightResponse;
import org.codeflow.veterineryproject.animal.repository.AnimalHealthRepository;
import org.codeflow.veterineryproject.common.api.BusinessRuleException;
import org.codeflow.veterineryproject.common.api.ConflictException;
import org.codeflow.veterineryproject.common.tenant.TenantContext;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class AnimalHealthService {

    private final AnimalHealthRepository repository;

    public AnimalHealthService(AnimalHealthRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public HealthSummary summary(UUID animalId) {
        requireAnimal(animalId);
        return new HealthSummary(repository.latestWeight(animalId).orElse(null), repository.allergies(animalId, true),
                repository.identifiers(animalId));
    }

    @Transactional(readOnly = true)
    public List<WeightResponse> weights(UUID animalId) {
        requireAnimal(animalId);
        return repository.weights(animalId);
    }

    @Transactional
    public WeightResponse addWeight(UUID animalId, WeightRequest request) {
        TenantContext.ContextValues ctx = TenantContext.require();
        requireAnimal(animalId);
        if (request.measuredAt() != null && request.measuredAt().isAfter(OffsetDateTime.now().plusMinutes(5))) {
            throw new BusinessRuleException("Ölçüm zamanı gelecekte olamaz.");
        }
        UUID id = repository.insertWeight(ctx.organizationId(), animalId, request.weightKg(), request.measuredAt(),
                request.measurementSource() == null ? "CLINIC" : request.measurementSource(), blank(request.note()),
                ctx.userId());
        return repository.weight(id).orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<AllergyResponse> allergies(UUID animalId, boolean activeOnly) {
        requireAnimal(animalId);
        return repository.allergies(animalId, activeOnly);
    }

    @Transactional
    public AllergyResponse addAllergy(UUID animalId, AllergyRequest request) {
        TenantContext.ContextValues ctx = TenantContext.require();
        requireAnimal(animalId);
        UUID id = repository.insertAllergy(ctx.organizationId(), animalId, request.substance().trim(),
                blank(request.reaction()), request.severity(), request.onsetDate(), ctx.userId());
        return repository.allergy(animalId, id).orElseThrow();
    }

    @Transactional
    public AllergyResponse resolveAllergy(UUID animalId, UUID allergyId) {
        requireAnimal(animalId);
        AllergyResponse allergy = repository.allergy(animalId, allergyId)
                .orElseThrow(() -> new EntityNotFoundException("Alerji kaydı bulunamadı."));
        if (allergy.active()) {
            repository.resolveAllergy(allergyId);
        }
        return repository.allergy(animalId, allergyId).orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<IdentifierResponse> identifiers(UUID animalId) {
        requireAnimal(animalId);
        return repository.identifiers(animalId);
    }

    @Transactional
    public IdentifierResponse addIdentifier(UUID animalId, IdentifierRequest request) {
        TenantContext.ContextValues ctx = TenantContext.require();
        requireAnimal(animalId);
        if (request.issuedAt() != null && request.expiresAt() != null && request.expiresAt().isBefore(request.issuedAt())) {
            throw new BusinessRuleException("Bitiş tarihi veriliş tarihinden önce olamaz.");
        }
        String value = request.identifierValue().trim();
        if ("MICROCHIP".equals(request.identifierType()) && !value.matches("[0-9]{9,15}")) {
            throw new BusinessRuleException("Mikroçip numarası 9-15 haneli rakamlardan oluşmalıdır.");
        }
        try {
            UUID id = repository.insertIdentifier(ctx.organizationId(), animalId, request.identifierType(), value,
                    request.issuedAt(), request.expiresAt(), blank(request.issuingAuthority()));
            return repository.identifier(id).orElseThrow();
        } catch (DuplicateKeyException ex) {
            throw new ConflictException("Bu kimlik numarası başka bir hastaya kayıtlı.");
        }
    }

    private void requireAnimal(UUID animalId) {
        if (!repository.animalVisible(animalId)) {
            throw new EntityNotFoundException("Hasta bulunamadı.");
        }
    }

    private static String blank(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
