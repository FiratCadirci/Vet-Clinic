package org.codeflow.veterineryproject.vaccine.service;

import jakarta.persistence.EntityNotFoundException;
import org.codeflow.veterineryproject.common.api.BusinessRuleException;
import org.codeflow.veterineryproject.common.api.ConflictException;
import org.codeflow.veterineryproject.common.audit.AuditRecorder;
import org.codeflow.veterineryproject.common.pagination.PageResponse;
import org.codeflow.veterineryproject.common.tenant.TenantContext;
import org.codeflow.veterineryproject.user.service.VeterinarianService;
import org.codeflow.veterineryproject.vaccine.dto.CreateProtocolRequest;
import org.codeflow.veterineryproject.vaccine.dto.ProtocolDoseRequest;
import org.codeflow.veterineryproject.vaccine.dto.ProtocolResponse;
import org.codeflow.veterineryproject.vaccine.dto.VaccineScheduleTemplate;
import org.codeflow.veterineryproject.vaccine.factory.VaccineScheduleFactories;
import org.codeflow.veterineryproject.vaccine.factory.VaccineScheduleFactory.AnimalProfile;
import org.codeflow.veterineryproject.vaccine.repository.VaccineJdbcRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Aşı şablonları (protokoller): taslak oluşturulur, hekim onayıyla kullanıma açılır. */
@Service
public class VaccineProtocolService {

    private static final String NOT_FOUND = "Aşı protokolü bulunamadı.";

    private final VaccineJdbcRepository repository;
    private final VeterinarianService veterinarians;
    private final VaccineScheduleFactories factories;
    private final AuditRecorder audit;

    public VaccineProtocolService(VaccineJdbcRepository repository, VeterinarianService veterinarians,
                                  VaccineScheduleFactories factories, AuditRecorder audit) {
        this.repository = repository;
        this.veterinarians = veterinarians;
        this.factories = factories;
        this.audit = audit;
    }

    @Transactional
    public ProtocolResponse create(CreateProtocolRequest request) {
        UUID organizationId = TenantContext.require().organizationId();
        if (!repository.speciesExists(request.speciesId())) {
            throw new BusinessRuleException("Tür bulunamadı.");
        }
        validateDoses(request);
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        UUID id = UUID.randomUUID();
        try {
            repository.insertProtocol(id, organizationId, code, request.name().trim(), request.speciesId(),
                    repository.nextVersion(code), request.boosterIntervalDays(), request.notes());
            for (ProtocolDoseRequest dose : request.doses()) {
                repository.insertDose(organizationId, id, dose.doseNumber(), dose.vaccineName().trim(),
                        dose.recommendedAgeDays(), dose.intervalFromPreviousDays(), dose.notes());
            }
        } catch (DuplicateKeyException ex) {
            throw new ConflictException("Aynı kod ve sürümde protokol zaten var.");
        }
        return repository.findProtocol(id).orElseThrow();
    }

    @Transactional(readOnly = true)
    public PageResponse<ProtocolResponse> list(UUID speciesId, String status, Pageable pageable) {
        return PageResponse.from(new PageImpl<>(repository.findProtocols(speciesId, status, pageable.getPageSize(), pageable.getOffset()),
                pageable, repository.countProtocols(speciesId, status)));
    }

    @Transactional(readOnly = true)
    public ProtocolResponse get(UUID id) {
        return repository.findProtocol(id).orElseThrow(() -> new EntityNotFoundException(NOT_FOUND));
    }

    /** Onay, klinik hekiminin tıbbi takvimi doğruladığı anlamına gelir; aynı kodlu önceki onaylı sürüm emekliye ayrılır. */
    @Transactional
    public ProtocolResponse approve(UUID id) {
        ProtocolResponse protocol = get(id);
        if (!"DRAFT".equals(protocol.status())) {
            throw new ConflictException("Yalnızca taslak protokol onaylanabilir.");
        }
        UUID vetId = veterinarians.requireCurrentVeterinarianId();
        repository.approve(id, vetId);
        audit.record("VACCINE_PROTOCOL_APPROVE", "VACCINE_PROTOCOL", id, TenantContext.require().clinicId());
        return get(id);
    }

    @Transactional
    public ProtocolResponse retire(UUID id) {
        ProtocolResponse protocol = get(id);
        if (!"APPROVED".equals(protocol.status())) {
            throw new ConflictException("Yalnızca onaylı protokol emekliye ayrılabilir.");
        }
        repository.retire(id);
        return get(id);
    }

    @Transactional(readOnly = true)
    public VaccineScheduleTemplate templateFor(UUID animalId) {
        AnimalProfile animal = repository.findAnimalProfile(animalId).orElseThrow(() -> new EntityNotFoundException("Hasta bulunamadı."));
        UUID speciesId = repository.findAnimalSpeciesId(animalId).orElseThrow();
        return factories.forSpecies(animal.speciesCode())
                .create(animal, repository.findApprovedForSpecies(speciesId), LocalDate.now());
    }

    private void validateDoses(CreateProtocolRequest request) {
        Set<Integer> numbers = new HashSet<>();
        for (ProtocolDoseRequest dose : request.doses()) {
            if (!numbers.add(dose.doseNumber())) {
                throw new BusinessRuleException("Doz numaraları tekil olmalıdır.");
            }
        }
        for (int n = 1; n <= numbers.size(); n++) {
            if (!numbers.contains(n)) {
                throw new BusinessRuleException("Doz numaraları 1'den başlayarak ardışık olmalıdır.");
            }
        }
    }
}
