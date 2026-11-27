package org.codeflow.veterineryproject.staff.service;

import jakarta.persistence.EntityNotFoundException;
import org.codeflow.veterineryproject.common.api.BusinessRuleException;
import org.codeflow.veterineryproject.common.api.ConflictException;
import org.codeflow.veterineryproject.common.audit.AuditRecorder;
import org.codeflow.veterineryproject.common.tenant.TenantContext;
import org.codeflow.veterineryproject.staff.dto.WorkingHoursDto.ReplaceRequest;
import org.codeflow.veterineryproject.staff.dto.WorkingHoursDto.VeterinarianOption;
import org.codeflow.veterineryproject.staff.dto.WorkingHoursDto.WorkingHourResponse;
import org.codeflow.veterineryproject.staff.repository.WorkingHoursRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * FG-E1: hekim ve şube bazında haftalık çalışma saatleri. Hedef şube X-Clinic-Id'dir (JWT'deki şubelerden biri olmalı);
 * hekimin aynı saatte iki şubede çalışmasını DB'deki EXCLUDE kısıtı engeller, burada yalnızca 409'a çevrilir.
 */
@Service
public class WorkingHoursService {

    private static final String EXCLUSION_VIOLATION = "23P01";

    private final WorkingHoursRepository repository;
    private final AuditRecorder audit;

    public WorkingHoursService(WorkingHoursRepository repository, AuditRecorder audit) {
        this.repository = repository;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<WorkingHourResponse> list(UUID clinicId, UUID veterinarianId) {
        return repository.list(clinicId, veterinarianId);
    }

    @Transactional(readOnly = true)
    public List<VeterinarianOption> veterinarians(UUID clinicId) {
        return repository.veterinariansAt(clinicId == null ? TenantContext.require().clinicId() : clinicId);
    }

    @Transactional
    public List<WorkingHourResponse> replace(ReplaceRequest request) {
        TenantContext.ContextValues ctx = TenantContext.require();
        WeeklySchedule schedule = WeeklySchedule.of(request.hours());
        if (!repository.veterinarianExists(request.veterinarianId())) {
            throw new EntityNotFoundException("Hekim bulunamadı.");
        }
        if (!repository.veterinarianAssignedTo(request.veterinarianId(), ctx.clinicId())) {
            throw new BusinessRuleException("Hekim bu şubede görevli değil.");
        }
        repository.deleteFor(request.veterinarianId(), ctx.clinicId());
        try {
            for (WeeklySchedule.Slot slot : schedule.slots()) {
                repository.insert(ctx.organizationId(), ctx.clinicId(), request.veterinarianId(), slot.dayOfWeek(), slot.start(), slot.end());
            }
        } catch (DataAccessException ex) {
            if (EXCLUSION_VIOLATION.equals(sqlState(ex))) {
                throw new ConflictException("Hekim bu saatlerde başka bir şubede çalışıyor.", "WORKING_HOURS_CONFLICT",
                        Map.of("veterinarianId", request.veterinarianId()));
            }
            throw ex;
        }
        audit.record("WORKING_HOURS_REPLACE", "veterinarian", request.veterinarianId(), ctx.clinicId());
        return repository.list(ctx.clinicId(), request.veterinarianId());
    }

    private static String sqlState(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql) {
                return sql.getSQLState();
            }
        }
        return null;
    }
}
