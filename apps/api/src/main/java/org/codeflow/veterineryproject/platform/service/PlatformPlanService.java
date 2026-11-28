package org.codeflow.veterineryproject.platform.service;

import jakarta.persistence.EntityNotFoundException;
import org.codeflow.veterineryproject.common.api.ConflictException;
import org.codeflow.veterineryproject.platform.dto.PlatformDto.CreatePlanRequest;
import org.codeflow.veterineryproject.platform.dto.PlatformDto.PlanResponse;
import org.codeflow.veterineryproject.platform.dto.PlatformDto.UpdatePlanRequest;
import org.codeflow.veterineryproject.platform.repository.PlatformPlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * FG-K5 paket yönetimi. Abonelikler fiyat ve limitleri kendi satırlarına kopyaladığı için paket güncellemesi mevcut
 * aboneliklere yansımaz; kullanılan paket silinemez (pasifleştirilir).
 */
@Service
public class PlatformPlanService {

    public static final String CODE_TAKEN = "PLAN_CODE_TAKEN";
    public static final String IN_USE = "PLAN_IN_USE";

    private final PlatformPlanRepository repository;

    public PlatformPlanService(PlatformPlanRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<PlanResponse> list() {
        return repository.findAll();
    }

    @Transactional
    public PlanResponse create(CreatePlanRequest request) {
        CreatePlanRequest normalized = new CreatePlanRequest(request.code().trim(), request.name().trim(),
                request.billingPeriod(), request.price(), request.maxBranches(), request.maxUsers(), request.active());
        if (repository.codeExists(normalized.code())) {
            throw new ConflictException("Bu paket kodu zaten kullanılıyor.", CODE_TAKEN, Map.of("code", normalized.code()));
        }
        UUID id = repository.insert(normalized, !Boolean.FALSE.equals(request.active()));
        return get(id);
    }

    @Transactional
    public PlanResponse update(UUID id, UpdatePlanRequest request) {
        UpdatePlanRequest normalized = new UpdatePlanRequest(request.name().trim(), request.price(),
                request.maxBranches(), request.maxUsers(), request.active());
        if (repository.update(id, normalized) == 0) {
            throw new EntityNotFoundException("Paket bulunamadı.");
        }
        return get(id);
    }

    @Transactional
    public void delete(UUID id) {
        PlanResponse plan = get(id);
        if (plan.subscriptionCount() > 0) {
            throw new ConflictException("Bu paketi kullanan abonelikler var; paketi silmek yerine pasifleştirin.", IN_USE,
                    Map.of("subscriptionCount", plan.subscriptionCount()));
        }
        repository.delete(id);
    }

    private PlanResponse get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new EntityNotFoundException("Paket bulunamadı."));
    }
}
