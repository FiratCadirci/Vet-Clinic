package org.codeflow.veterineryproject.notification;

import org.codeflow.veterineryproject.common.tenant.TenantContext;
import org.codeflow.veterineryproject.common.test.ApiTestSupport;
import org.codeflow.veterineryproject.common.test.ClinicalTestData;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * Bildirim IT'lerinin ortak tabanı: sahte e-posta/push ile tek Spring bağlamı, test verisi ve
 * arka planda çalışan bildirim işlerini beklemek için yardımcılar. Zamanlanmış görev testte kapalıdır.
 */
@SpringBootTest(properties = "app.notifications.vaccine-reminder-cron=-")
@AutoConfigureMockMvc
@Import(NotificationTestConfig.class)
public abstract class NotificationItSupport extends ApiTestSupport {

    @Autowired
    protected NotificationTestConfig.FakeMailSender mail;

    @Autowired
    protected NotificationTestConfig.FakePushGateway push;

    @Autowired
    protected ApplicationEventPublisher events;

    @Autowired
    protected PlatformTransactionManager transactionManager;

    @BeforeAll
    static void seedNotificationData() {
        NotificationTestData.seed(superUserDataSource());
    }

    @AfterAll
    static void cleanNotificationData() {
        NotificationTestData.cleanup(superUserDataSource());
    }

    @BeforeEach
    void resetFakes() {
        mail.failing = false;
        push.errors.clear();
    }

    /** Olayı, gerçek serviste olduğu gibi bir işlem içinde ve kullanıcının tenant bağlamında yayınlar. */
    protected void publishInTransaction(UUID org, UUID user, UUID clinic, Object event) {
        try (TenantContext.Scope ignored = TenantContext.withTenant(org, user, clinic)) {
            new TransactionTemplate(transactionManager).executeWithoutResult(s -> events.publishEvent(event));
        }
    }

    protected static long count(String sql, Object... params) {
        return ClinicalTestData.count(superUserDataSource(), sql, params);
    }

    protected static String text(String sql, Object... params) {
        return ClinicalTestData.text(superUserDataSource(), sql, params);
    }

    /** Bildirimler commit sonrası arka planda gider; koşul sağlanana kadar (en çok 10 sn) bekler. */
    protected static void await(String description, BooleanSupplier condition) {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new AssertionError(ex);
            }
        }
        throw new AssertionError("Beklenen durum oluşmadı: " + description);
    }

    /** Bekleyen (PENDING) gönderim kalmayana kadar bekler; ardından durum sorguları kararlıdır. */
    protected static void awaitDeliveries(String where, Object... params) {
        await("gönderimler tamamlandı: " + where, () -> count("SELECT count(*) FROM vet.notification_outbox WHERE " + where, params) > 0
                && count("SELECT count(*) FROM vet.notification_outbox WHERE status = 'PENDING' AND " + where, params) == 0);
    }
}
