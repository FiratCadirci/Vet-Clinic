package org.codeflow.veterineryproject.vaccine.service;

import jakarta.persistence.EntityNotFoundException;
import org.codeflow.veterineryproject.common.api.BusinessRuleException;
import org.codeflow.veterineryproject.common.api.ConflictException;
import org.codeflow.veterineryproject.common.audit.AuditRecorder;
import org.codeflow.veterineryproject.common.pagination.PageResponse;
import org.codeflow.veterineryproject.common.tenant.TenantContext;
import org.codeflow.veterineryproject.user.service.VeterinarianService;
import org.codeflow.veterineryproject.vaccine.dto.AnimalVaccineScheduleResponse;
import org.codeflow.veterineryproject.vaccine.dto.CreateProtocolRequest;
import org.codeflow.veterineryproject.vaccine.dto.GenerateProtocolsRequest;
import org.codeflow.veterineryproject.vaccine.dto.ProtocolDoseRequest;
import org.codeflow.veterineryproject.vaccine.dto.ProtocolResponse;
import org.codeflow.veterineryproject.vaccine.dto.UpdateProtocolRequest;
import org.codeflow.veterineryproject.vaccine.dto.VaccineScheduleTemplate;
import org.codeflow.veterineryproject.vaccine.factory.ProtocolDraft;
import org.codeflow.veterineryproject.vaccine.factory.VaccineScheduleCalculator;
import org.codeflow.veterineryproject.vaccine.factory.VaccineScheduleFactories;
import org.codeflow.veterineryproject.vaccine.factory.VaccineScheduleFactory.AnimalProfile;
import org.codeflow.veterineryproject.vaccine.repository.VaccineJdbcRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Aşı şablonları (protokoller): taslak oluşturulur, hekim onayıyla kullanıma açılır. */
@Service
public class VaccineProtocolService {

    private static final String NOT_FOUND = "Aşı protokolü bulunamadı.";
    private static final String DRAFT_NOTE = "Fabrika taslağı; kullanılmadan önce klinik hekimi tarafından doğrulanıp onaylanmalıdır.";

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
        validateDoses(request.doses());
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

    /**
     * FG-D1: türün fabrikasından (Factory Method) onaysız taslak protokoller üretir. Aynı kodun emekliye
     * ayrılmamış sürümü zaten varsa o seri atlanır (tekrar üretim mevcut şablonu bozmaz).
     *
     * @return yeni oluşturulan taslaklar
     */
    @Transactional
    public List<ProtocolResponse> generate(GenerateProtocolsRequest request) {
        UUID organizationId = TenantContext.require().organizationId();
        String speciesCode = repository.findSpeciesCode(request.speciesId())
                .orElseThrow(() -> new BusinessRuleException("Tür bulunamadı."));
        List<ProtocolDraft> drafts = factories.forSpecies(speciesCode).createDraft();
        if (drafts.isEmpty()) {
            throw new BusinessRuleException("Bu tür için hazır aşı takvimi yok; kalemleri elle ekleyin.");
        }
        List<ProtocolResponse> created = new ArrayList<>();
        for (ProtocolDraft draft : drafts) {
            String code = speciesCode.toUpperCase(Locale.ROOT) + "-" + draft.code();
            if (repository.activeCodeExists(code)) {
                continue;
            }
            UUID id = UUID.randomUUID();
            try {
                repository.insertProtocol(id, organizationId, code, draft.vaccineName(), request.speciesId(),
                        repository.nextVersion(code), draft.boosterIntervalDays(), DRAFT_NOTE);
            } catch (DuplicateKeyException ex) {
                throw new ConflictException("Aynı kod ve sürümde protokol zaten var.");
            }
            for (int n = 1; n <= draft.doseCount(); n++) {
                repository.insertDose(organizationId, id, n, draft.vaccineName(), n == 1 ? draft.firstDoseAgeDays() : null,
                        n == 1 ? null : draft.doseIntervalDays(), null);
            }
            created.add(repository.findProtocol(id).orElseThrow());
        }
        audit.record("VACCINE_PROTOCOL_GENERATE", "SPECIES", request.speciesId(), TenantContext.require().clinicId());
        return created;
    }

    /**
     * FG-D2: kalem değişikliği onayı sıfırlar. Değişiklik aynı kodla yeni bir TASLAK sürüm olarak yazılır ve
     * eski sürüm emekliye ayrılır; böylece hekim yeni sürümü onaylayana kadar bu seri aşı kaydında kullanılamaz.
     * Eski sürüm silinmez: geçmiş aşı kayıtları kendi dozlarına bağlı kalır.
     */
    @Transactional
    public ProtocolResponse update(UUID id, UpdateProtocolRequest request) {
        UUID organizationId = TenantContext.require().organizationId();
        ProtocolResponse current = get(id);
        if ("RETIRED".equals(current.status())) {
            throw new ConflictException("Emekliye ayrılmış protokol değiştirilemez.");
        }
        validateDoses(request.doses());
        UUID newId = UUID.randomUUID();
        repository.retire(id);
        try {
            repository.insertProtocol(newId, organizationId, current.code(), request.name().trim(), current.speciesId(),
                    repository.nextVersion(current.code()), request.boosterIntervalDays(), request.notes());
        } catch (DuplicateKeyException ex) {
            throw new ConflictException("Protokol eşzamanlı olarak değiştirildi; sayfayı yenileyip tekrar deneyin.");
        }
        for (ProtocolDoseRequest dose : request.doses()) {
            repository.insertDose(organizationId, newId, dose.doseNumber(), dose.vaccineName().trim(),
                    dose.recommendedAgeDays(), dose.intervalFromPreviousDays(), dose.notes());
        }
        audit.record("VACCINE_PROTOCOL_UPDATE", "VACCINE_PROTOCOL", newId, TenantContext.require().clinicId());
        return get(newId);
    }

    /** Hiç kullanılmamış taslak silinebilir; kullanılmış veya onaylı protokol emekliye ayrılır. */
    @Transactional
    public void deleteDraft(UUID id) {
        ProtocolResponse protocol = get(id);
        if (!"DRAFT".equals(protocol.status())) {
            throw new ConflictException("Yalnızca taslak protokol silinebilir; onaylı protokolü emekliye ayırın.");
        }
        if (repository.dosesUsedByRecords(id)) {
            throw new ConflictException("Aşı kaydında kullanılmış protokol silinemez.");
        }
        repository.deleteProtocol(id);
        audit.record("VACCINE_PROTOCOL_DELETE", "VACCINE_PROTOCOL", id, TenantContext.require().clinicId());
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

    /**
     * FG-D4: hastanın aşı takvimi. Fabrika (türün en erken yaş kuralı) onaylı protokollerden şablonu üretir,
     * hesaplayıcı gerçek kayıtlarla birleştirir: yapılan / planlı / geciken dozlar, rapel ve en yakın vade.
     */
    @Transactional(readOnly = true)
    public AnimalVaccineScheduleResponse scheduleFor(UUID animalId) {
        AnimalProfile animal = repository.findAnimalProfile(animalId).orElseThrow(() -> new EntityNotFoundException("Hasta bulunamadı."));
        UUID speciesId = repository.findAnimalSpeciesId(animalId).orElseThrow();
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Europe/Istanbul"));
        VaccineScheduleTemplate template = factories.forSpecies(animal.speciesCode())
                .create(animal, repository.findApprovedForSpecies(speciesId), today);
        VaccineScheduleCalculator.Result result = VaccineScheduleCalculator.calculate(template, repository.findGiven(animalId), today);
        boolean pending = repository.countProtocols(speciesId, "DRAFT") > 0;
        return new AnimalVaccineScheduleResponse(animalId, animal.speciesCode(), animal.birthDate(), pending, result.items(),
                result.others(), result.nextDue());
    }

    private void validateDoses(List<ProtocolDoseRequest> doses) {
        Set<Integer> numbers = new HashSet<>();
        for (ProtocolDoseRequest dose : doses) {
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
