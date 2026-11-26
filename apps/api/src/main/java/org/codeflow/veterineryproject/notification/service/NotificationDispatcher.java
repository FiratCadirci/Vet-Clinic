package org.codeflow.veterineryproject.notification.service;

import jakarta.annotation.PreDestroy;
import org.codeflow.veterineryproject.common.tenant.TenantContext;
import org.codeflow.veterineryproject.notification.model.NotificationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Olay kaynaklı bildirim işlerini arka planda çalıştırır: SMTP/Expo gecikmesi HTTP yanıtını bekletmez,
 * hatalar iş işlemine ulaşmaz (FG-I4 / FOG-4). İşi kuyruğa veren iş parçacığının tenant bağlamı işe taşınır.
 * <p>
 * Zarif kapanış: bean yok edilirken (NotificationService ve veri kaynağından önce, çünkü onlara bağımlıdır)
 * yeni iş alınmaz, kuyruktaki ve yarım kalan işlerin bitmesi beklenir.
 */
@Component
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

    private final NotificationService notifications;
    private final ThreadPoolTaskExecutor executor;

    public NotificationDispatcher(NotificationService notifications,
                                  @Value("${app.notifications.shutdown-await-seconds:30}") int awaitSeconds) {
        this.notifications = notifications;
        this.executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("notification-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(1000);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(awaitSeconds);
        executor.initialize();
    }

    /**
     * @param composer işçi iş parçacığında, olayı yayınlayanın tenant bağlamıyla çalışır; alıcıları çözüp mesajı üretir
     */
    public void dispatch(String description, Supplier<Optional<NotificationMessage>> composer) {
        TenantContext.ContextValues ctx = TenantContext.get().orElse(null);
        if (ctx == null || ctx.organizationId() == null) {
            log.warn("{} bildirimi atlandı: organizasyon bağlamı yok", description);
            return;
        }
        try {
            executor.execute(() -> run(description, ctx, composer));
        } catch (TaskRejectedException ex) {
            log.error("{} bildirimi kuyruğa alınamadı: {}", description, ex.getMessage());
        }
    }

    private void run(String description, TenantContext.ContextValues ctx, Supplier<Optional<NotificationMessage>> composer) {
        try (TenantContext.Scope ignored = TenantContext.withTenant(ctx.organizationId(), ctx.userId(), ctx.clinicId())) {
            composer.get().ifPresent(notifications::send);
        } catch (RuntimeException ex) {
            log.error("{} bildirimi oluşturulamadı: {}", description, ex.getMessage(), ex);
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }
}
