package org.codeflow.veterineryproject.notification;

import org.codeflow.veterineryproject.common.event.DomainEvents.EncounterClosedEvent;
import org.codeflow.veterineryproject.common.event.DomainEvents.StockLowEvent;
import org.codeflow.veterineryproject.common.tenant.TenantContext;
import org.codeflow.veterineryproject.notification.event.NotificationEventHandler;
import org.codeflow.veterineryproject.notification.event.NotificationEventListener;
import org.codeflow.veterineryproject.notification.model.NotificationMessage;
import org.codeflow.veterineryproject.notification.model.NotificationRecipient;
import org.codeflow.veterineryproject.notification.model.NotificationType;
import org.codeflow.veterineryproject.notification.service.NotificationDispatcher;
import org.codeflow.veterineryproject.notification.service.NotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.PayloadApplicationEvent;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/** Observer kayıt yapısı ve arka plan işçisi: olay → doğru handler, tenant bağlamı taşınır, hata yutulur, kapanış bekler. */
class NotificationDispatcherTest {

    private static final UUID ORG = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();

    private final NotificationService service = mock(NotificationService.class);
    private final NotificationDispatcher dispatcher = new NotificationDispatcher(service, 10);

    @AfterEach
    void tearDown() {
        dispatcher.shutdown();
        TenantContext.clear();
    }

    @Test
    void listenerRoutesEventToItsHandlerOnly_andPropagatesTenantContextToWorker() throws Exception {
        AtomicReference<TenantContext.ContextValues> seen = new AtomicReference<>();
        List<Object> handled = new CopyOnWriteArrayList<>();
        NotificationEventHandler<StockLowEvent> stock = handler(StockLowEvent.class, e -> {
            seen.set(TenantContext.get().orElse(null));
            handled.add(e);
        });
        NotificationEventListener listener = new NotificationEventListener(List.of(stock), dispatcher);
        StockLowEvent event = new StockLowEvent(ORG, UUID.randomUUID(), UUID.randomUUID(), "Ürün", BigDecimal.ONE, BigDecimal.TEN);

        TenantContext.set(ORG, USER, null);
        listener.onDomainEvent(new PayloadApplicationEvent<>(this, event));
        listener.onDomainEvent(new PayloadApplicationEvent<>(this, new EncounterClosedEvent(ORG, UUID.randomUUID(), null, null, null)));

        verify(service, timeout(2000)).send(any());
        assertThat(handled).containsExactly(event);
        assertThat(seen.get().organizationId()).isEqualTo(ORG);
        assertThat(seen.get().userId()).isEqualTo(USER);
    }

    @Test
    void failingComposerIsLoggedAndSwallowed_nextJobsStillRun() {
        TenantContext.set(ORG, USER, null);
        dispatcher.dispatch("bozuk", () -> {
            throw new IllegalStateException("veritabanı yok");
        });
        dispatcher.dispatch("sağlam", () -> Optional.of(message()));

        verify(service, timeout(2000)).send(any());
    }

    @Test
    void shutdownWaitsForInFlightNotificationJobs() throws Exception {
        TenantContext.set(ORG, USER, null);
        CountDownLatch started = new CountDownLatch(1);
        AtomicBoolean finished = new AtomicBoolean();
        dispatcher.dispatch("yavaş", () -> {
            started.countDown();
            try {
                Thread.sleep(500);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            finished.set(true);
            return Optional.empty();
        });
        assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();

        dispatcher.shutdown();

        assertThat(finished).isTrue();
    }

    @Test
    void withoutOrganizationContextNothingIsQueued() {
        AtomicBoolean ran = new AtomicBoolean();
        dispatcher.dispatch("bağlamsız", () -> {
            ran.set(true);
            return Optional.empty();
        });
        dispatcher.shutdown();
        assertThat(ran).isFalse();
    }

    private static NotificationMessage message() {
        return NotificationMessage.of(NotificationType.STOCK_LOW, "Başlık", "Metin", null, null,
                List.of(NotificationRecipient.staff(USER, "Stok", null)));
    }

    private static <E> NotificationEventHandler<E> handler(Class<E> type, java.util.function.Consumer<E> onEvent) {
        return new NotificationEventHandler<>() {
            @Override
            public Class<E> eventType() {
                return type;
            }

            @Override
            public Optional<NotificationMessage> compose(E event) {
                onEvent.accept(event);
                return Optional.of(message());
            }
        };
    }
}
