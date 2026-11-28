package org.codeflow.veterineryproject.subscription.service;

import org.codeflow.veterineryproject.common.tenant.TenantContext;
import org.codeflow.veterineryproject.subscription.dto.SubscriptionDto.RenewalRunResponse;
import org.codeflow.veterineryproject.subscription.model.SubscriptionStatus;
import org.codeflow.veterineryproject.subscription.repository.SubscriptionJdbcRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

/**
 * FG-K3 günlük abonelik kontrolü (her gün 01:00, Europe/Istanbul): dönemi biten deneme/aktif abonelik PAST_DUE olur,
 * dönem sonunda iptal istenmişse CANCELLED. RLS delinmez: organizasyon listesi yalnız kimlik döndüren SECURITY DEFINER
 * fonksiyondan gelir, güncelleme her organizasyonun kendi tenant bağlamında ve ayrı transaction'da yapılır; bir
 * organizasyondaki hata diğerlerini etkilemez. Platform yöneticisi aynı koşuyu elle tetikleyebilir.
 */
@Component
public class SubscriptionRenewalJob {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionRenewalJob.class);

    private final SubscriptionJdbcRepository repository;
    private final TransactionTemplate tx;

    public SubscriptionRenewalJob(SubscriptionJdbcRepository repository, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.tx = new TransactionTemplate(transactionManager);
    }

    @Scheduled(cron = "${app.subscriptions.renewal-cron:0 0 1 * * *}", zone = "Europe/Istanbul")
    public void runDaily() {
        RenewalRunResponse result = run();
        log.info("Abonelik kontrolü tamamlandı: {} organizasyon, {} gecikmiş, {} iptal",
                result.organizations(), result.pastDue(), result.cancelled());
    }

    public RenewalRunResponse run() {
        int organizations = 0;
        int pastDue = 0;
        int cancelled = 0;
        for (UUID organizationId : repository.findOrganizationsWithEndedPeriods()) {
            try (TenantContext.Scope ignored = TenantContext.withTenant(organizationId, null, null)) {
                List<SubscriptionStatus> changed = tx.execute(s -> repository.closeEndedPeriods(organizationId));
                organizations++;
                for (SubscriptionStatus status : changed == null ? List.<SubscriptionStatus>of() : changed) {
                    if (status == SubscriptionStatus.PAST_DUE) {
                        pastDue++;
                    } else {
                        cancelled++;
                    }
                }
            } catch (RuntimeException ex) {
                log.error("Abonelik kontrolü yapılamadı organizationId={}: {}", organizationId, ex.getMessage(), ex);
            }
        }
        return new RenewalRunResponse(organizations, pastDue, cancelled);
    }
}
