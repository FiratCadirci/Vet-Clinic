package org.codeflow.veterineryproject.encounter.service;

import jakarta.persistence.EntityNotFoundException;
import org.codeflow.veterineryproject.common.api.BusinessRuleException;
import org.codeflow.veterineryproject.common.api.ConflictException;
import org.codeflow.veterineryproject.common.audit.AuditRecorder;
import org.codeflow.veterineryproject.common.event.DomainEvents.AppointmentStatusChangedEvent;
import org.codeflow.veterineryproject.common.event.DomainEvents.EncounterClosedEvent;
import org.codeflow.veterineryproject.common.pagination.PageResponse;
import org.codeflow.veterineryproject.common.sequence.SequenceCounterRepository;
import org.codeflow.veterineryproject.common.tenant.TenantContext;
import org.codeflow.veterineryproject.encounter.dto.AddendumRequest;
import org.codeflow.veterineryproject.encounter.dto.AddendumResponse;
import org.codeflow.veterineryproject.encounter.dto.CloseEncounterRequest;
import org.codeflow.veterineryproject.encounter.dto.CreateEncounterRequest;
import org.codeflow.veterineryproject.encounter.dto.DiagnosisRequest;
import org.codeflow.veterineryproject.encounter.dto.EncounterResponse;
import org.codeflow.veterineryproject.encounter.dto.EncounterStatus;
import org.codeflow.veterineryproject.encounter.dto.ExaminationRequest;
import org.codeflow.veterineryproject.encounter.dto.HistoryItemResponse;
import org.codeflow.veterineryproject.encounter.dto.PlanRequest;
import org.codeflow.veterineryproject.encounter.dto.StartFromAppointmentRequest;
import org.codeflow.veterineryproject.encounter.dto.TreatmentRequest;
import org.codeflow.veterineryproject.encounter.dto.TreatmentResponse;
import org.codeflow.veterineryproject.encounter.dto.UpdateEncounterRequest;
import org.codeflow.veterineryproject.encounter.repository.AppointmentLinkRepository;
import org.codeflow.veterineryproject.encounter.repository.AppointmentLinkRepository.AppointmentRow;
import org.codeflow.veterineryproject.encounter.repository.EncounterJdbcRepository;
import org.codeflow.veterineryproject.encounter.workflow.AdmissionStep;
import org.codeflow.veterineryproject.encounter.workflow.CloseStep;
import org.codeflow.veterineryproject.encounter.workflow.DiagnosisStep;
import org.codeflow.veterineryproject.encounter.workflow.ExaminationStep;
import org.codeflow.veterineryproject.encounter.workflow.PlanStep;
import org.codeflow.veterineryproject.user.service.VeterinarianService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class EncounterService {

    private static final String NOT_FOUND = "Muayene bulunamadı.";
    private static final String ANIMAL_NOT_FOUND = "Hasta bulunamadı.";
    private static final Set<String> STARTABLE_APPOINTMENT_STATUSES = Set.of("CONFIRMED", "CHECKED_IN");

    private final EncounterJdbcRepository repository;
    private final AppointmentLinkRepository appointments;
    private final SequenceCounterRepository sequences;
    private final VeterinarianService veterinarians;
    private final AuditRecorder audit;
    private final ApplicationEventPublisher events;
    private final AdmissionStep admissionStep;
    private final ExaminationStep examinationStep;
    private final DiagnosisStep diagnosisStep;
    private final PlanStep planStep;
    private final CloseStep closeStep;

    public EncounterService(EncounterJdbcRepository repository, AppointmentLinkRepository appointments,
                            SequenceCounterRepository sequences, VeterinarianService veterinarians, AuditRecorder audit,
                            ApplicationEventPublisher events, AdmissionStep admissionStep, ExaminationStep examinationStep,
                            DiagnosisStep diagnosisStep, PlanStep planStep, CloseStep closeStep) {
        this.repository = repository;
        this.appointments = appointments;
        this.sequences = sequences;
        this.veterinarians = veterinarians;
        this.audit = audit;
        this.events = events;
        this.admissionStep = admissionStep;
        this.examinationStep = examinationStep;
        this.diagnosisStep = diagnosisStep;
        this.planStep = planStep;
        this.closeStep = closeStep;
    }

    /** Randevusuz (yürüyerek gelen) muayene. */
    @Transactional
    public EncounterResponse startWalkIn(CreateEncounterRequest request) {
        return start(request.animalId(), null, request.encounterType(), request.chiefComplaint(), request.history());
    }

    /** Aynı klinik grubundaki onaylı randevudan muayene başlatır; randevu IN_PROGRESS olur. */
    @Transactional
    public EncounterResponse startFromAppointment(UUID appointmentId, StartFromAppointmentRequest request) {
        TenantContext.ContextValues ctx = TenantContext.require();
        AppointmentRow appointment = appointments.findForUpdate(appointmentId)
                .orElseThrow(() -> new EntityNotFoundException("Randevu bulunamadı."));
        if (!STARTABLE_APPOINTMENT_STATUSES.contains(appointment.status())) {
            throw new ConflictException("'%s' durumundaki randevudan muayene başlatılamaz.".formatted(appointment.status()));
        }
        if (repository.hasOpenEncounterForAppointment(appointmentId)) {
            throw new ConflictException("Bu randevu için zaten açık bir muayene var.");
        }
        String complaint = request != null && request.chiefComplaint() != null && !request.chiefComplaint().isBlank()
                ? request.chiefComplaint() : appointment.reason();
        EncounterResponse created = start(appointment.animalId(), appointmentId, "CONSULTATION", complaint,
                request == null ? null : request.history());
        appointments.changeStatus(ctx.organizationId(), appointmentId, appointment.status(), "IN_PROGRESS", ctx.userId(),
                "Muayene başlatıldı");
        events.publishEvent(new AppointmentStatusChangedEvent(ctx.organizationId(), appointmentId, appointment.status(),
                "IN_PROGRESS", appointment.clinicId()));
        return created;
    }

    @Transactional
    public void completeAppointment(UUID appointmentId, CloseEncounterRequest request) {
        if (!appointments.exists(appointmentId)) {
            throw new EntityNotFoundException("Randevu bulunamadı.");
        }
        List<EncounterResponse> openEncounters = repository.findByAppointmentId(appointmentId).stream()
                .filter(encounter -> encounter.status() != EncounterStatus.CLOSED)
                .toList();
        if (openEncounters.size() != 1) {
            throw new ConflictException(openEncounters.isEmpty()
                    ? "Randevu için tamamlanabilecek açık bir muayene bulunamadı."
                    : "Randevu için birden fazla açık muayene bulundu.");
        }
        close(openEncounters.getFirst().id(), request);
    }

    @Transactional(readOnly = true)
    public EncounterResponse get(UUID id) {
        return details(repository.findById(id).orElseThrow(() -> new EntityNotFoundException(NOT_FOUND)));
    }

    /** Randevuya bağlı tüm muayeneler (tedavi ve addendum dahil), başlangıç sırasıyla. */
    @Transactional(readOnly = true)
    public List<EncounterResponse> listByAppointment(UUID appointmentId) {
        if (!appointments.exists(appointmentId)) {
            throw new EntityNotFoundException("Randevu bulunamadı.");
        }
        return repository.findByAppointmentId(appointmentId).stream().map(this::details).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<EncounterResponse> list(UUID animalId, EncounterStatus status, Pageable pageable) {
        List<EncounterResponse> rows = repository.search(animalId, status, pageable.getPageSize(), pageable.getOffset());
        return PageResponse.from(new PageImpl<>(rows, pageable, repository.count(animalId, status)));
    }

    @Transactional
    public EncounterResponse examination(UUID id, ExaminationRequest request) {
        return step(id, current -> examinationStep.execute(current, request.examinationSummary()));
    }

    @Transactional
    public EncounterResponse diagnosis(UUID id, DiagnosisRequest request) {
        return step(id, current -> diagnosisStep.execute(current, request.assessment()));
    }

    @Transactional
    public EncounterResponse plan(UUID id, PlanRequest request) {
        return step(id, current -> planStep.execute(current, new PlanStep.Input(request.treatmentPlan(), request.followUpAt())));
    }

    @Transactional
    public EncounterResponse close(UUID id, CloseEncounterRequest request) {
        TenantContext.ContextValues ctx = TenantContext.require();
        EncounterResponse current = loadForWrite(id);
        veterinarians.requireCurrentVeterinarianId();
        AppointmentRow linkedAppointment = null;
        if (current.appointmentId() != null) {
            linkedAppointment = appointments.findForUpdate(current.appointmentId())
                    .orElseThrow(() -> new EntityNotFoundException("Randevu bulunamadı."));
            if (!"IN_PROGRESS".equals(linkedAppointment.status())) {
                throw new ConflictException("Muayene yalnızca IN_PROGRESS durumundaki randevu için kapatılabilir.");
            }
        }
        EncounterResponse closed = closeStep.execute(current, request == null ? null : request.vetNotes());
        repository.updateContent(closed);

        audit.record("ENCOUNTER_CLOSE", "ENCOUNTER", id, current.clinicId());
        events.publishEvent(new EncounterClosedEvent(ctx.organizationId(), id, current.animalId(), current.appointmentId(), current.clinicId()));
        if (linkedAppointment != null) {
            appointments.changeStatus(ctx.organizationId(), linkedAppointment.id(), "IN_PROGRESS", "COMPLETED",
                    ctx.userId(), "Muayene kapatıldı");
            events.publishEvent(new AppointmentStatusChangedEvent(ctx.organizationId(), linkedAppointment.id(),
                    "IN_PROGRESS", "COMPLETED", linkedAppointment.clinicId()));
        }
        return details(repository.findById(id).orElseThrow());
    }

    /** Kapatılmamış muayenenin alanlarını (durumu değiştirmeden) günceller. */
    @Transactional
    public EncounterResponse update(UUID id, UpdateEncounterRequest request) {
        EncounterResponse current = loadForWrite(id);
        requireOpen(current);
        veterinarians.requireCurrentVeterinarianId();
        EncounterResponse updated = current.withContent(
                pick(request.chiefComplaint(), current.chiefComplaint(), "Şikâyet"),
                pick(request.history(), current.history(), null),
                pick(request.examinationSummary(), current.examinationSummary(), "Fizik muayene"),
                pick(request.assessment(), current.assessment(), "Tanı"),
                pick(request.treatmentPlan(), current.treatmentPlan(), "Tedavi planı"),
                pick(request.vetNotes(), current.vetNotes(), null),
                request.followUpAt() != null ? request.followUpAt() : current.followUpAt());
        repository.updateContent(updated);
        return details(repository.findById(id).orElseThrow());
    }

    /** Kapatılmış (veya açık) muayeneye düzeltme/ek not ekler; asıl kayıt değişmez. */
    @Transactional
    public AddendumResponse addAddendum(UUID id, AddendumRequest request) {
        TenantContext.ContextValues ctx = TenantContext.require();
        EncounterResponse current = loadForWrite(id);
        veterinarians.requireCurrentVeterinarianId();
        return repository.insertAddendum(ctx.organizationId(), id, current.clinicId(), request.note().trim(), ctx.userId());
    }

    @Transactional
    public void delete(UUID id) {
        TenantContext.ContextValues ctx = TenantContext.require();
        EncounterResponse current = loadForWrite(id);
        if (current.status() == EncounterStatus.CLOSED) {
            throw new ConflictException("Kapatılmış muayene silinemez.");
        }
        if (repository.countDependents(id) > 0) {
            throw new ConflictException("Muayeneye bağlı kayıtlar (tedavi, aşı, addendum, ek dosya) olduğu için silinemez.");
        }
        repository.delete(id);
        audit.record("ENCOUNTER_DELETE", "ENCOUNTER", id, current.clinicId());
        if (current.appointmentId() != null) {
            appointments.findForUpdate(current.appointmentId()).ifPresent(appointment -> {
                if ("IN_PROGRESS".equals(appointment.status())) {
                    appointments.changeStatus(ctx.organizationId(), appointment.id(), "IN_PROGRESS", "CHECKED_IN",
                            ctx.userId(), "Muayene silindi");
                    events.publishEvent(new AppointmentStatusChangedEvent(ctx.organizationId(), appointment.id(),
                            "IN_PROGRESS", "CHECKED_IN", appointment.clinicId()));
                }
            });
        }
    }

    @Transactional
    public TreatmentResponse addTreatment(UUID encounterId, TreatmentRequest request) {
        TenantContext.ContextValues ctx = TenantContext.require();
        EncounterResponse encounter = loadForWrite(encounterId);
        requireOpen(encounter);
        veterinarians.requireCurrentVeterinarianId();
        if (Boolean.TRUE.equals(request.prescribed()) && !"MEDICATION".equals(request.itemType())) {
            throw new BusinessRuleException("Yalnızca ilaç kalemi reçeteye yazılabilir.");
        }
        if (request.dose() != null && (request.doseUnit() == null || request.doseUnit().isBlank())) {
            throw new BusinessRuleException("Doz girildiğinde doz birimi zorunludur.");
        }
        if (request.productId() != null && !repository.productVisible(request.productId())) {
            throw new BusinessRuleException("Ürün bulunamadı.");
        }
        TreatmentResponse item = new TreatmentResponse(null, encounterId, request.itemType(), request.name().trim(),
                request.productId(), request.dose(), blankToNull(request.doseUnit()), blankToNull(request.route()),
                blankToNull(request.frequencyText()), request.durationDays(), Boolean.TRUE.equals(request.prescribed()),
                blankToNull(request.notes()), request.performedAt() != null ? request.performedAt() : OffsetDateTime.now(),
                ctx.userId(), null);
        return repository.insertTreatment(ctx.organizationId(), encounterId, item);
    }

    @Transactional(readOnly = true)
    public List<TreatmentResponse> listTreatments(UUID encounterId) {
        repository.findById(encounterId).orElseThrow(() -> new EntityNotFoundException(NOT_FOUND));
        return repository.findTreatments(encounterId);
    }

    @Transactional
    public void deleteTreatment(UUID encounterId, UUID treatmentId) {
        EncounterResponse encounter = loadForWrite(encounterId);
        requireOpen(encounter);
        UUID owner = repository.findTreatmentEncounterId(treatmentId).orElseThrow(() -> new EntityNotFoundException("Tedavi kalemi bulunamadı."));
        if (!owner.equals(encounterId)) {
            throw new EntityNotFoundException("Tedavi kalemi bulunamadı.");
        }
        repository.deleteTreatment(treatmentId);
        audit.record("TREATMENT_DELETE", "ENCOUNTER_TREATMENT", treatmentId, encounter.clinicId());
    }

    @Transactional(readOnly = true)
    public PageResponse<HistoryItemResponse> history(UUID animalId, Pageable pageable) {
        if (!repository.animalVisible(animalId)) {
            throw new EntityNotFoundException(ANIMAL_NOT_FOUND);
        }
        List<HistoryItemResponse> rows = repository.history(animalId, pageable.getPageSize(), pageable.getOffset());
        return PageResponse.from(new PageImpl<>(rows, pageable, repository.historyCount(animalId)));
    }

    /** Başka şubenin kaydı yalnızca okunabilir; değişiklik için kaydı oluşturan şubeden (X-Clinic-Id) işlem yapılmalıdır. */
    public EncounterResponse loadForWrite(UUID id) {
        TenantContext.ContextValues ctx = TenantContext.require();
        EncounterResponse encounter = repository.findByIdForUpdate(id).orElseThrow(() -> new EntityNotFoundException(NOT_FOUND));
        if (!encounter.clinicId().equals(ctx.clinicId())) {
            throw new AccessDeniedException("Başka şubenin kaydı bu şubeden değiştirilemez.");
        }
        return encounter;
    }

    private EncounterResponse start(UUID animalId, UUID appointmentId, String type, String complaint, String history) {
        TenantContext.ContextValues ctx = TenantContext.require();
        UUID vetId = veterinarians.requireCurrentVeterinarianId();
        if (!repository.animalVisible(animalId)) {
            throw new BusinessRuleException(ANIMAL_NOT_FOUND);
        }
        UUID id = UUID.randomUUID();
        String number = "E-%06d".formatted(sequences.next(ctx.organizationId(), "ENCOUNTER"));
        EncounterResponse draft = new EncounterResponse(id, number, animalId, null, null, ctx.clinicId(), appointmentId, vetId,
                type == null ? "CONSULTATION" : type, null, OffsetDateTime.now(), null, null, null, null, null, null, null,
                null, ctx.userId(), null, null, List.of(), List.of());
        EncounterResponse admitted = admissionStep.execute(draft, new AdmissionStep.Input(complaint, history));
        try {
            repository.insert(ctx.organizationId(), admitted);
        } catch (DuplicateKeyException ex) {
            throw new ConflictException("Bu randevu için zaten açık bir muayene var.");
        }
        return details(repository.findById(id).orElseThrow());
    }

    private EncounterResponse step(UUID id, java.util.function.UnaryOperator<EncounterResponse> action) {
        EncounterResponse current = loadForWrite(id);
        veterinarians.requireCurrentVeterinarianId();
        EncounterResponse next = action.apply(current);
        repository.updateContent(next);
        return details(repository.findById(id).orElseThrow());
    }

    private void requireOpen(EncounterResponse encounter) {
        if (encounter.status() == EncounterStatus.CLOSED) {
            throw new ConflictException("Kapatılmış muayene değiştirilemez; düzeltme için addendum ekleyin.");
        }
    }

    private EncounterResponse details(EncounterResponse encounter) {
        return encounter.withDetails(repository.findAddenda(encounter.id()), repository.findTreatments(encounter.id()));
    }

    private String pick(String requested, String current, String requiredLabel) {
        if (requested == null) {
            return current;
        }
        if (requested.isBlank() && requiredLabel != null) {
            throw new BusinessRuleException(requiredLabel + " boş olamaz.");
        }
        return blankToNull(requested);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
