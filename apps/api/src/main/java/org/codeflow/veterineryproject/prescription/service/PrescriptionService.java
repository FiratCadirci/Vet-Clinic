package org.codeflow.veterineryproject.prescription.service;

import jakarta.persistence.EntityNotFoundException;
import org.codeflow.veterineryproject.common.api.BusinessRuleException;
import org.codeflow.veterineryproject.common.api.ConflictException;
import org.codeflow.veterineryproject.common.audit.AuditRecorder;
import org.codeflow.veterineryproject.common.sequence.SequenceCounterRepository;
import org.codeflow.veterineryproject.common.tenant.TenantContext;
import org.codeflow.veterineryproject.encounter.dto.EncounterResponse;
import org.codeflow.veterineryproject.encounter.dto.EncounterStatus;
import org.codeflow.veterineryproject.encounter.service.EncounterService;
import org.codeflow.veterineryproject.prescription.dto.PrescriptionItemRequest;
import org.codeflow.veterineryproject.prescription.dto.PrescriptionItemResponse;
import org.codeflow.veterineryproject.prescription.repository.PrescriptionJdbcRepository;
import org.codeflow.veterineryproject.prescription.repository.PrescriptionJdbcRepository.ProductRow;
import org.codeflow.veterineryproject.user.service.VeterinarianService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Muayene reçetesi (FG-C3): muayene başına tek reçete başlığı (ilk kalemle oluşur), kalemler ilaç ürün kartına bağlıdır.
 * Kapatılmış muayenenin reçetesi değiştirilemez; yazma, muayeneyi açan şubeden yapılır.
 */
@Service
public class PrescriptionService {

    private static final String MEDICATION = "MEDICATION";
    private static final String ITEM_NOT_FOUND = "Reçete kalemi bulunamadı.";

    private final PrescriptionJdbcRepository repository;
    private final EncounterService encounters;
    private final VeterinarianService veterinarians;
    private final SequenceCounterRepository sequences;
    private final AuditRecorder audit;

    public PrescriptionService(PrescriptionJdbcRepository repository, EncounterService encounters,
                               VeterinarianService veterinarians, SequenceCounterRepository sequences, AuditRecorder audit) {
        this.repository = repository;
        this.encounters = encounters;
        this.veterinarians = veterinarians;
        this.sequences = sequences;
        this.audit = audit;
    }

    @Transactional
    public PrescriptionItemResponse add(UUID encounterId, PrescriptionItemRequest request) {
        TenantContext.ContextValues ctx = TenantContext.require();
        EncounterResponse encounter = encounters.loadForWrite(encounterId);
        requireOpen(encounter);
        UUID vetId = veterinarians.requireCurrentVeterinarianId();
        ProductRow product = repository.findProduct(request.productId())
                .orElseThrow(() -> new BusinessRuleException("Ürün bulunamadı."));
        if (!MEDICATION.equals(product.productType())) {
            throw new BusinessRuleException("Reçeteye yalnızca ilaç ürünü yazılabilir.");
        }
        if (request.dose() != null && blankToNull(request.doseUnit()) == null) {
            throw new BusinessRuleException("Doz girildiğinde doz birimi zorunludur.");
        }
        // Muayene satırı loadForWrite ile kilitli: eşzamanlı ilk kalemler tek başlık oluşturur.
        UUID prescriptionId = repository.findPrescriptionId(encounterId).orElseGet(() -> {
            UUID id = UUID.randomUUID();
            String number = "RX-%06d".formatted(sequences.next(ctx.organizationId(), "PRESCRIPTION"));
            repository.insertPrescription(id, ctx.organizationId(), encounterId, encounter.animalId(), vetId, number);
            return id;
        });
        UUID itemId = UUID.randomUUID();
        repository.insertItem(itemId, ctx.organizationId(), prescriptionId, repository.nextLineNo(prescriptionId),
                product.id(), request.dose(), blankToNull(request.doseUnit()), blankToNull(request.route()),
                request.frequencyText().trim(), request.durationDays(), request.quantity(), blankToNull(request.instructions()));
        audit.record("PRESCRIPTION_ITEM_CREATE", "PRESCRIPTION_ITEM", itemId, encounter.clinicId());
        return repository.findItem(itemId).orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<PrescriptionItemResponse> list(UUID encounterId) {
        encounters.get(encounterId);
        return repository.findItemsByEncounter(encounterId);
    }

    @Transactional
    public void delete(UUID encounterId, UUID itemId) {
        EncounterResponse encounter = encounters.loadForWrite(encounterId);
        requireOpen(encounter);
        PrescriptionItemResponse item = repository.findItem(itemId)
                .filter(i -> i.encounterId().equals(encounterId))
                .orElseThrow(() -> new EntityNotFoundException(ITEM_NOT_FOUND));
        repository.deleteItem(itemId);
        repository.deleteIfEmpty(item.prescriptionId());
        audit.record("PRESCRIPTION_ITEM_DELETE", "PRESCRIPTION_ITEM", itemId, encounter.clinicId());
    }

    private static void requireOpen(EncounterResponse encounter) {
        if (encounter.status() == EncounterStatus.CLOSED) {
            throw new ConflictException("Kapatılmış muayenenin reçetesi değiştirilemez; düzeltme için addendum ekleyin.");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
