package org.codeflow.veterineryproject.appointment.service;

import jakarta.persistence.EntityNotFoundException;
import org.codeflow.veterineryproject.appointment.dto.AppointmentResponse;
import org.codeflow.veterineryproject.appointment.dto.CreateAppointmentRequest;
import org.codeflow.veterineryproject.appointment.dto.RescheduleRequest;
import org.codeflow.veterineryproject.appointment.model.AppointmentStatus;
import org.codeflow.veterineryproject.appointment.repository.AppointmentJdbcRepository;
import org.codeflow.veterineryproject.appointment.repository.AppointmentJdbcRepository.Filter;
import org.codeflow.veterineryproject.common.api.BusinessRuleException;
import org.codeflow.veterineryproject.common.audit.AuditRecorder;
import org.codeflow.veterineryproject.common.event.DomainEvents.AppointmentRescheduledEvent;
import org.codeflow.veterineryproject.common.event.DomainEvents.AppointmentStatusChangedEvent;
import org.codeflow.veterineryproject.common.pagination.PageResponse;
import org.codeflow.veterineryproject.common.sequence.SequenceCounterRepository;
import org.codeflow.veterineryproject.common.tenant.TenantContext;
import org.codeflow.veterineryproject.encounter.repository.AppointmentLinkRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class AppointmentService {

    private static final String NOT_FOUND = "Randevu bulunamadı.";
    private static final Set<String> APPOINTMENT_TYPES = Set.of(
            "EXAM", "VACCINATION", "SURGERY", "FOLLOW_UP", "GROOMING", "LAB", "IMAGING", "OTHER");
    private static final Set<String> BOOKING_CHANNELS = Set.of("STAFF", "PHONE", "WEB", "MOBILE", "WHATSAPP", "OTHER");

    private final AppointmentJdbcRepository repository;
    private final AppointmentLinkRepository links;
    private final SequenceCounterRepository sequences;
    private final AuditRecorder audit;
    private final ApplicationEventPublisher events;

    public AppointmentService(AppointmentJdbcRepository repository, AppointmentLinkRepository links,
                              SequenceCounterRepository sequences, AuditRecorder audit, ApplicationEventPublisher events) {
        this.repository = repository;
        this.links = links;
        this.sequences = sequences;
        this.audit = audit;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public PageResponse<AppointmentResponse> list(Filter filter, Pageable pageable) {
        validateFilterRange(filter);
        List<String> statuses = parseStatuses(filter.statuses());
        String type = blankToNull(filter.type());
        String channel = blankToNull(filter.bookingChannel());
        validateOptionalValue(type, APPOINTMENT_TYPES, "Randevu türü");
        validateOptionalValue(channel, BOOKING_CHANNELS, "Randevu kanalı");
        Filter normalized = new Filter(filter.from(), filter.to(), filter.clinicId(), filter.veterinarianId(),
                statuses, type, channel, filter.ownerId(), filter.animalId());
        List<AppointmentResponse> content = repository.search(normalized, pageable);
        return PageResponse.from(new PageImpl<>(content, pageable, repository.count(normalized)));
    }

    private void validateFilterRange(Filter filter) {
        if (filter.from() != null && filter.to() != null && !filter.to().isAfter(filter.from())) {
            throw new IllegalArgumentException("endDateTime, startDateTime değerinden sonra olmalıdır.");
        }
    }

    private List<String> parseStatuses(List<String> values) {
        Set<String> normalized = new LinkedHashSet<>();
        if (values == null) {
            return List.of();
        }
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            for (String item : value.split(",")) {
                String status = item.trim();
                if (status.isEmpty()) {
                    continue;
                }
                parse(status);
                normalized.add(status);
            }
        }
        return new ArrayList<>(normalized);
    }

    private void validateOptionalValue(String value, Set<String> allowed, String label) {
        if (value != null && !value.isBlank() && !allowed.contains(value)) {
            throw new IllegalArgumentException("Geçersiz " + label.toLowerCase() + ": " + value);
        }
    }

    @Transactional(readOnly = true)
    public AppointmentResponse get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new EntityNotFoundException(NOT_FOUND));
    }

    @Transactional
    public AppointmentResponse create(CreateAppointmentRequest request) {
        TenantContext.ContextValues ctx = TenantContext.require();
        if (ctx.clinicId() == null) {
            throw new BusinessRuleException("Randevu için şube (X-Clinic-Id) gereklidir.");
        }
        validateRange(request.startsAt(), request.endsAt());
        if (!repository.animalExists(request.animalId())) {
            throw new EntityNotFoundException("Hasta bulunamadı.");
        }
        UUID clientId = resolveClient(request);
        if (request.veterinarianId() != null && !repository.veterinarianActive(request.veterinarianId())) {
            throw new BusinessRuleException("Veteriner hekim bulunamadı veya pasif.");
        }
        UUID id = UUID.randomUUID();
        String number = "A-%06d".formatted(sequences.next(ctx.organizationId(), "APPOINTMENT"));
        repository.insert(id, ctx.organizationId(), ctx.clinicId(), clientId, request.animalId(), request.veterinarianId(),
                number, request.appointmentType() == null ? "EXAM" : request.appointmentType(), request.startsAt(),
                request.endsAt(), blankToNull(request.reason()), blankToNull(request.internalNotes()),
                request.bookingChannel() == null ? "STAFF" : request.bookingChannel(), ctx.userId());
        audit.record("APPOINTMENT_CREATE", "APPOINTMENT", id, ctx.clinicId());
        return get(id);
    }

    @Transactional
    public AppointmentResponse confirm(UUID id) {
        return transition(id, AppointmentStatus.CONFIRMED, "Randevu onaylandı", null);
    }

    @Transactional
    public AppointmentResponse checkIn(UUID id) {
        return transition(id, AppointmentStatus.CHECKED_IN, "Hasta kabul edildi", null);
    }

    @Transactional
    public AppointmentResponse noShow(UUID id) {
        return transition(id, AppointmentStatus.NO_SHOW, "Hasta gelmedi", null);
    }

    @Transactional
    public AppointmentResponse cancel(UUID id, String reason) {
        return transition(id, AppointmentStatus.CANCELLED, "Randevu iptal edildi: " + reason.trim(), reason.trim());
    }

    @Transactional
    public AppointmentResponse reschedule(UUID id, RescheduleRequest request) {
        TenantContext.ContextValues ctx = TenantContext.require();
        AppointmentLinkRepository.AppointmentRow row = lock(id);
        AppointmentStatus current = parse(row.status());
        if (current != AppointmentStatus.REQUESTED && current != AppointmentStatus.BOOKED
                && current != AppointmentStatus.CONFIRMED) {
            throw new BusinessRuleException("Yalnızca bekleyen veya onaylı randevu ertelenebilir. Durum: " + current);
        }
        validateRange(request.startsAt(), request.endsAt());
        UUID vet = request.veterinarianId();
        if (vet != null && !repository.veterinarianActive(vet)) {
            throw new BusinessRuleException("Veteriner hekim bulunamadı veya pasif.");
        }
        AppointmentResponse existing = get(id);
        repository.reschedule(id, request.startsAt(), request.endsAt(), vet == null ? existing.veterinarianId() : vet);
        audit.record("APPOINTMENT_RESCHEDULE", "APPOINTMENT", id, row.clinicId());
        events.publishEvent(new AppointmentRescheduledEvent(ctx.organizationId(), id, row.clinicId()));
        return get(id);
    }

    private AppointmentResponse transition(UUID id, AppointmentStatus target, String note, String cancelReason) {
        TenantContext.ContextValues ctx = TenantContext.require();
        AppointmentLinkRepository.AppointmentRow row = lock(id);
        AppointmentStatus current = parse(row.status());
        if (!current.canMoveTo(target)) {
            throw new BusinessRuleException("Randevu %s durumundan %s durumuna geçirilemez.".formatted(current, target));
        }
        links.changeStatus(ctx.organizationId(), id, current.name(), target.name(), ctx.userId(), note);
        if (cancelReason != null) {
            repository.markCancelled(id, cancelReason);
        }
        audit.record("APPOINTMENT_" + target.name(), "APPOINTMENT", id, row.clinicId());
        events.publishEvent(new AppointmentStatusChangedEvent(ctx.organizationId(), id, current.name(), target.name(),
                row.clinicId()));
        return get(id);
    }

    private AppointmentLinkRepository.AppointmentRow lock(UUID id) {
        return links.findForUpdate(id).orElseThrow(() -> new EntityNotFoundException(NOT_FOUND));
    }

    private UUID resolveClient(CreateAppointmentRequest request) {
        if (request.clientId() != null) {
            if (!repository.isCurrentOwner(request.animalId(), request.clientId())) {
                throw new BusinessRuleException("Müşteri bu hastanın güncel sahibi değil.");
            }
            return request.clientId();
        }
        return repository.findOwnerClientId(request.animalId())
                .orElseThrow(() -> new BusinessRuleException("Hastanın güncel sahibi bulunamadı."));
    }

    private static void validateRange(OffsetDateTime start, OffsetDateTime end) {
        if (!end.isAfter(start)) {
            throw new BusinessRuleException("Bitiş zamanı başlangıçtan sonra olmalıdır.");
        }
    }

    private static AppointmentStatus parse(String value) {
        try {
            return AppointmentStatus.valueOf(value);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Geçersiz randevu durumu: " + value);
        }
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
