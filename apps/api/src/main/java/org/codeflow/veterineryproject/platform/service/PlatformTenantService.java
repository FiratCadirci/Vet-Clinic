package org.codeflow.veterineryproject.platform.service;

import jakarta.persistence.EntityNotFoundException;
import org.codeflow.veterineryproject.common.api.BusinessRuleException;
import org.codeflow.veterineryproject.platform.dto.PlatformDto.TenantResponse;
import org.codeflow.veterineryproject.platform.dto.PlatformDto.UpdateTenantSubscriptionRequest;
import org.codeflow.veterineryproject.platform.repository.PlatformPlanRepository;
import org.codeflow.veterineryproject.platform.repository.PlatformTenantRepository;
import org.codeflow.veterineryproject.platform.repository.PlatformTenantRepository.SubscriptionRef;
import org.codeflow.veterineryproject.subscription.dto.SubscriptionDto.RenewalRunResponse;
import org.codeflow.veterineryproject.subscription.model.SubscriptionStatus;
import org.codeflow.veterineryproject.subscription.service.SubscriptionRenewalJob;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Platform yöneticisinin klinik (organizasyon) işlemleri: liste, abonelik değiştirme / askıya alma ve günlük abonelik
 * kontrolünün elle tetiklenmesi. Tıbbi veriye dokunmaz; abonelik yazmaları V6 platform log'una düşer.
 */
@Service
public class PlatformTenantService {

    private final PlatformTenantRepository tenants;
    private final PlatformPlanRepository plans;
    private final SubscriptionRenewalJob renewalJob;

    public PlatformTenantService(PlatformTenantRepository tenants, PlatformPlanRepository plans,
                                 SubscriptionRenewalJob renewalJob) {
        this.tenants = tenants;
        this.plans = plans;
        this.renewalJob = renewalJob;
    }

    @Transactional(readOnly = true)
    public List<TenantResponse> list() {
        return tenants.findAll();
    }

    /** Sırasıyla paket, dönem sonu ve durum uygulanır (durum, güncel dönem sonuna göre yenileme tarihini belirler). */
    @Transactional
    public TenantResponse updateSubscription(UUID organizationId, UpdateTenantSubscriptionRequest request) {
        if (!tenants.exists(organizationId)) {
            throw new EntityNotFoundException("Klinik bulunamadı.");
        }
        SubscriptionRef subscription = tenants.lockLatestSubscription(organizationId)
                .orElseThrow(() -> new EntityNotFoundException("Kliniğin aboneliği bulunamadı."));
        if (request.planId() != null) {
            plans.findById(request.planId()).orElseThrow(() -> new EntityNotFoundException("Paket bulunamadı."));
            tenants.applyPlan(subscription.id(), request.planId());
        }
        if (request.currentPeriodEnd() != null) {
            if (!request.currentPeriodEnd().isAfter(subscription.currentPeriodStart())) {
                throw new BusinessRuleException("Dönem sonu, dönem başlangıcından sonra olmalıdır.");
            }
            tenants.applyPeriodEnd(subscription.id(), request.currentPeriodEnd());
        }
        if (request.status() != null) {
            SubscriptionStatus status = SubscriptionStatus.valueOf(request.status());
            tenants.applyStatus(subscription.id(), status.name(), status.isTerminal());
        }
        return tenants.findById(organizationId).orElseThrow(() -> new EntityNotFoundException("Klinik bulunamadı."));
    }

    /** Zamanlanmış görevle aynı koşu; her organizasyon kendi transaction'ında işlenir. */
    public RenewalRunResponse runRenewalCheck() {
        return renewalJob.run();
    }
}
