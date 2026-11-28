package org.codeflow.veterineryproject.vaccine.service;

import jakarta.persistence.EntityNotFoundException;
import org.codeflow.veterineryproject.common.api.BusinessRuleException;
import org.codeflow.veterineryproject.common.api.ConflictException;
import org.codeflow.veterineryproject.common.audit.AuditRecorder;
import org.codeflow.veterineryproject.common.event.DomainEvents.VaccineRecordedEvent;
import org.codeflow.veterineryproject.common.pagination.PageResponse;
import org.codeflow.veterineryproject.common.tenant.TenantContext;
import org.codeflow.veterineryproject.encounter.dto.EncounterResponse;
import org.codeflow.veterineryproject.encounter.dto.EncounterStatus;
import org.codeflow.veterineryproject.encounter.service.EncounterService;
import org.codeflow.veterineryproject.inventory.dto.InventoryDto.ConsumptionResult;
import org.codeflow.veterineryproject.inventory.dto.InventoryDto.LotAllocation;
import org.codeflow.veterineryproject.inventory.service.StockConsumptionService;
import org.codeflow.veterineryproject.user.service.VeterinarianService;
import org.codeflow.veterineryproject.vaccine.dto.DueVaccineResponse;
import org.codeflow.veterineryproject.vaccine.dto.ProtocolDoseResponse;
import org.codeflow.veterineryproject.vaccine.dto.ProtocolResponse;
import org.codeflow.veterineryproject.vaccine.dto.RecordVaccineRequest;
import org.codeflow.veterineryproject.vaccine.dto.VaccineRecordResponse;
import org.codeflow.veterineryproject.vaccine.factory.NextDoseCalculator;
import org.codeflow.veterineryproject.vaccine.repository.VaccineJdbcRepository;
import org.codeflow.veterineryproject.vaccine.repository.VaccineJdbcRepository.VaccineProduct;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class VaccinationService {

    private static final Set<String> DUE_STATUSES = Set.of("OVERDUE", "UPCOMING", "ALL");
    private static final String VACCINE_PRODUCT = "VACCINE";
    private static final String STOCK_REFERENCE = "VACCINE_RECORD";
    /** Bir aşı uygulaması stoktan bir birim (flakon/doz) düşer. */
    private static final BigDecimal ONE_UNIT = BigDecimal.ONE;

    private final VaccineJdbcRepository repository;
    private final EncounterService encounterService;
    private final VeterinarianService veterinarians;
    private final AuditRecorder audit;
    private final ApplicationEventPublisher events;
    private final StockConsumptionService stock;

    public VaccinationService(VaccineJdbcRepository repository, EncounterService encounterService,
                              VeterinarianService veterinarians, AuditRecorder audit, ApplicationEventPublisher events,
                              StockConsumptionService stock) {
        this.repository = repository;
        this.encounterService = encounterService;
        this.veterinarians = veterinarians;
        this.audit = audit;
        this.events = events;
        this.stock = stock;
    }

    @Transactional
    public VaccineRecordResponse record(RecordVaccineRequest request) {
        TenantContext.ContextValues ctx = TenantContext.require();
        EncounterResponse encounter;
        try {
            encounter = encounterService.loadForWrite(request.encounterId());
        } catch (EntityNotFoundException ex) {
            throw new BusinessRuleException("Muayene bulunamadı.");
        }
        if (encounter.status() == EncounterStatus.CLOSED) {
            throw new ConflictException("Kapatılmış muayeneye aşı kaydı eklenemez.");
        }
        if (repository.animalDeceased(encounter.animalId())) {
            throw new ConflictException("Vefat etmiş hastaya aşı kaydı eklenemez.", "PATIENT_DECEASED", java.util.Map.of());
        }
        UUID vetId = veterinarians.requireCurrentVeterinarianId();

        OffsetDateTime administeredAt = request.administeredAt() != null ? request.administeredAt() : OffsetDateTime.now();
        if (administeredAt.isAfter(OffsetDateTime.now().plusMinutes(5))) {
            throw new BusinessRuleException("Uygulama tarihi gelecekte olamaz.");
        }
        LocalDate administeredDate = administeredAt.toLocalDate();

        String vaccineName = blankToNull(request.vaccineName());
        ProtocolResponse protocol = null;
        Integer doseNumber = null;
        if (request.protocolDoseId() != null) {
            protocol = repository.findProtocolByDose(request.protocolDoseId())
                    .orElseThrow(() -> new BusinessRuleException("Protokol dozu bulunamadı."));
            UUID animalSpecies = repository.findAnimalSpeciesId(encounter.animalId()).orElseThrow();
            if (!"APPROVED".equals(protocol.status()) || !protocol.speciesId().equals(animalSpecies)) {
                throw new BusinessRuleException("Protokol onaylı değil veya hastanın türüne ait değil.");
            }
            ProtocolDoseResponse dose = protocol.doses().stream().filter(d -> d.id().equals(request.protocolDoseId())).findFirst().orElseThrow();
            doseNumber = dose.doseNumber();
            if (vaccineName == null) {
                vaccineName = dose.vaccineName();
            }
        }
        VaccineProduct product = null;
        if (request.productId() != null) {
            product = repository.findProduct(request.productId())
                    .filter(p -> VACCINE_PRODUCT.equals(p.productType()))
                    .orElseThrow(() -> new BusinessRuleException("Aşı ürünü bulunamadı."));
            if (vaccineName == null) {
                vaccineName = product.name();
            }
        }
        if (vaccineName == null) {
            throw new BusinessRuleException("Aşı adı veya protokol dozu zorunludur.");
        }

        LocalDate nextDue;
        String source;
        if (request.nextDueAt() != null) {
            if (request.nextDueAt().isBefore(administeredDate)) {
                throw new BusinessRuleException("Sonraki doz tarihi uygulama tarihinden önce olamaz.");
            }
            nextDue = request.nextDueAt();
            source = "VET_OVERRIDE";
        } else {
            Optional<LocalDate> computed = doseNumber == null ? Optional.empty()
                    : NextDoseCalculator.compute(administeredDate, protocol, doseNumber);
            nextDue = computed.orElse(null);
            source = computed.isPresent() ? "TEMPLATE" : "NONE";
        }

        UUID id = UUID.randomUUID();
        String batchNumber = blankToNull(request.batchNumber());
        UUID lotId = null;
        UUID transactionId = null;
        if (product != null) {
            // FG-D3: aynı transaction içinde FEFO ile düşülür; yetersiz stokta 409 ve kayıt dahil hiçbir şey yazılmaz.
            // Stok servisi belirli lot seçtirmez: lot takipli üründe kayda gerçekten düşülen (FEFO) lot yazılır,
            // elle girilen lot numarası bu durumda dikkate alınmaz (kayıt ile stok hareketi tutarlı kalır).
            ConsumptionResult consumed = stock.consume(encounter.clinicId(), product.id(), ONE_UNIT, STOCK_REFERENCE, id);
            transactionId = consumed.transactionId();
            Optional<LotAllocation> lot = consumed.allocations().stream().filter(a -> a.lotId() != null).findFirst();
            if (lot.isPresent()) {
                lotId = lot.get().lotId();
                batchNumber = lot.get().lotNumber();
            }
        }
        repository.insertRecord(id, ctx.organizationId(), encounter.animalId(), encounter.id(), encounter.clinicId(),
                request.protocolDoseId(), vaccineName, batchNumber, administeredAt, vetId,
                request.dose(), blankToNull(request.doseUnit()), blankToNull(request.route()),
                blankToNull(request.administrationSite()), blankToNull(request.certificateNo()), blankToNull(request.notes()),
                nextDue, source, ctx.userId(), product == null ? null : product.id(), lotId, transactionId);
        audit.record("VACCINE_RECORD_CREATE", "VACCINE_RECORD", id, encounter.clinicId());
        events.publishEvent(new VaccineRecordedEvent(ctx.organizationId(), id, encounter.animalId(), encounter.id(),
                encounter.clinicId(), nextDue));
        return repository.findRecord(id).orElseThrow();
    }

    @Transactional
    public void delete(UUID id) {
        VaccineRecordResponse record = repository.findRecord(id).orElseThrow(() -> new EntityNotFoundException("Aşı kaydı bulunamadı."));
        EncounterResponse encounter = encounterService.loadForWrite(record.encounterId());
        if (encounter.status() == EncounterStatus.CLOSED) {
            throw new ConflictException("Kapatılmış muayenenin aşı kaydı silinemez.");
        }
        repository.deleteRecord(id);
        audit.record("VACCINE_RECORD_DELETE", "VACCINE_RECORD", id, record.clinicId());
    }

    @Transactional(readOnly = true)
    public PageResponse<VaccineRecordResponse> listByAnimal(UUID animalId, Pageable pageable) {
        requireAnimal(animalId);
        return PageResponse.from(new PageImpl<>(repository.findRecordsByAnimal(animalId, pageable.getPageSize(), pageable.getOffset()),
                pageable, repository.countRecordsByAnimal(animalId)));
    }

    @Transactional(readOnly = true)
    public List<VaccineRecordResponse> listByEncounter(UUID encounterId) {
        return repository.findRecordsByEncounter(encounterId);
    }

    /** animalId verilmezse klinik grubundaki tüm hastalar (RLS kapsamı) taranır. */
    @Transactional(readOnly = true)
    public PageResponse<DueVaccineResponse> due(UUID animalId, String status, Integer days, Pageable pageable) {
        if (animalId != null) {
            requireAnimal(animalId);
        }
        String resolved = status == null ? "ALL" : status.toUpperCase();
        if (!DUE_STATUSES.contains(resolved)) {
            throw new BusinessRuleException("Geçersiz durum; OVERDUE, UPCOMING veya ALL olmalı.");
        }
        int window = days == null ? 30 : Math.max(0, Math.min(days, 365));
        return PageResponse.from(new PageImpl<>(repository.findDue(animalId, resolved, window, pageable.getPageSize(), pageable.getOffset()),
                pageable, repository.countDue(animalId, resolved, window)));
    }

    private void requireAnimal(UUID animalId) {
        repository.findAnimalSpeciesId(animalId).orElseThrow(() -> new EntityNotFoundException("Hasta bulunamadı."));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
