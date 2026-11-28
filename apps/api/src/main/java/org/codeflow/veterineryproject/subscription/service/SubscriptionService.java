package org.codeflow.veterineryproject.subscription.service;

import jakarta.persistence.EntityNotFoundException;
import org.codeflow.veterineryproject.common.api.ConflictException;
import org.codeflow.veterineryproject.common.audit.AuditRecorder;
import org.codeflow.veterineryproject.common.tenant.TenantContext;
import org.codeflow.veterineryproject.common.time.ApiDateTime;
import org.codeflow.veterineryproject.subscription.dto.PlanDto.PlanOption;
import org.codeflow.veterineryproject.subscription.dto.SubscriptionDto.BillingEventResponse;
import org.codeflow.veterineryproject.subscription.dto.SubscriptionDto.CurrentSubscriptionResponse;
import org.codeflow.veterineryproject.subscription.dto.SubscriptionDto.PaymentResult;
import org.codeflow.veterineryproject.subscription.model.SubscriptionStatus;
import org.codeflow.veterineryproject.subscription.repository.SubscriptionJdbcRepository;
import org.codeflow.veterineryproject.subscription.repository.SubscriptionJdbcRepository.LockedSubscription;
import org.codeflow.veterineryproject.subscription.repository.SubscriptionJdbcRepository.PlanTerms;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Klinik yöneticisinin abonelik işlemleri (Senaryo 6): mevcut abonelik + limit/kullanım, paket değiştirme (FG-K2),
 * simüle ödeme ve ödeme geçmişi (FG-K3), dönem sonunda iptal. Organizasyon her zaman oturumdan alınır.
 */
@Service
public class SubscriptionService {

    public static final String ALREADY_CANCELLED = "SUBSCRIPTION_ALREADY_CANCELLED";
    private static final String ENTITY = "subscription";

    private final SubscriptionJdbcRepository repository;
    private final PlanLimitService planLimits;
    private final AuditRecorder audit;

    public SubscriptionService(SubscriptionJdbcRepository repository, PlanLimitService planLimits, AuditRecorder audit) {
        this.repository = repository;
        this.planLimits = planLimits;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public CurrentSubscriptionResponse current() {
        UUID organizationId = TenantContext.require().organizationId();
        return new CurrentSubscriptionResponse(repository.findLatestView(organizationId).orElse(null), planLimits.usage());
    }

    @Transactional(readOnly = true)
    public List<PlanOption> plans() {
        return planLimits.activePlans();
    }

    @Transactional(readOnly = true)
    public List<BillingEventResponse> payments() {
        return repository.listBillingEvents(TenantContext.require().organizationId());
    }

    /**
     * FG-K2: yeni paketin limitleri mevcut kullanımı karşılamıyorsa 409. Organizasyon kilitlendiği için kontrol ile
     * değişiklik arasında şube/personel eklenemez. Yeni fiyat bir sonraki ödemede, yeni limitler hemen geçerlidir.
     */
    @Transactional
    public CurrentSubscriptionResponse changePlan(UUID planId) {
        TenantContext.ContextValues ctx = TenantContext.require();
        PlanTerms plan = repository.findActivePlan(planId)
                .orElseThrow(() -> new EntityNotFoundException("Paket bulunamadı."));
        repository.lockOrganization(ctx.organizationId());
        LockedSubscription subscription = requireSubscription(ctx.organizationId());
        new PlanCapacity(plan.name(), plan.maxBranches(), plan.maxUsers()).assertAccepts(planLimits.usage());
        repository.changePlan(subscription.id(), plan);
        audit.record("SUBSCRIPTION_PLAN_CHANGE", ENTITY, subscription.id(), ctx.clinicId());
        return current();
    }

    /** FG-K3: simüle ödeme (gerçek tahsilat yok) bir dönemi öder, ödeme kaydı yazar ve aboneliği aktif yapar. */
    @Transactional
    public PaymentResult pay() {
        TenantContext.ContextValues ctx = TenantContext.require();
        LockedSubscription subscription = requireSubscription(ctx.organizationId());
        OffsetDateTime now = OffsetDateTime.now();
        BillingCycle.PaidPeriod period = BillingCycle.next(subscription.status(), subscription.currentPeriodEnd(),
                subscription.billingPeriod(), now, ApiDateTime.ZONE);
        BillingEventResponse payment = repository.insertSimulatedPayment(ctx.organizationId(), subscription.id(),
                subscription.price(), subscription.currency(), period.start(), period.end(), now);
        repository.markPaid(subscription.id(),
                period.extendsCurrentPeriod() ? subscription.currentPeriodStart() : period.start(), period.end());
        audit.record("SUBSCRIPTION_PAYMENT", ENTITY, subscription.id(), ctx.clinicId());
        return new PaymentResult(payment, current());
    }

    /**
     * İptal dönem sonunda gerçekleşir: abonelik dönem bitene kadar kullanılır, yenileme olmaz; günlük görev dönem
     * sonunda CANCELLED yapar. Dönemi zaten bitmiş (gecikmiş) abonelik hemen iptal olur. Ödeme iptali geri alır.
     */
    @Transactional
    public CurrentSubscriptionResponse cancel() {
        TenantContext.ContextValues ctx = TenantContext.require();
        LockedSubscription subscription = requireSubscription(ctx.organizationId());
        if (subscription.status().isTerminal() || subscription.cancelRequested()) {
            throw new ConflictException("Abonelik zaten iptal edilmiş.", ALREADY_CANCELLED, Map.of());
        }
        if (subscription.status() == SubscriptionStatus.PAST_DUE) {
            repository.cancelNow(subscription.id());
        } else {
            repository.requestCancellation(subscription.id());
        }
        audit.record("SUBSCRIPTION_CANCEL", ENTITY, subscription.id(), ctx.clinicId());
        return current();
    }

    private LockedSubscription requireSubscription(UUID organizationId) {
        return repository.lockLatest(organizationId)
                .orElseThrow(() -> new EntityNotFoundException("Abonelik bulunamadı."));
    }
}
