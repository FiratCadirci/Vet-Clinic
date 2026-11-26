package org.codeflow.veterineryproject.notification.event;

import org.codeflow.veterineryproject.notification.model.NotificationMessage;
import org.codeflow.veterineryproject.notification.service.NotificationDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * OBSERVER: modüller {@code ApplicationEventPublisher.publishEvent(...)} ile olay yayınlar ve bildirimden habersizdir.
 * Bu dinleyici olayı iş işlemi commit edildikten SONRA alır (AFTER_COMMIT; geri alınan işlem bildirim üretmez),
 * olayın türüne kayıtlı {@link NotificationEventHandler}'ları bulur ve işi arka plan kuyruğuna verir.
 * Yeni olay için bu sınıf değişmez: yeni bir handler bileşeni yeterlidir (OCP).
 */
@Component
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    private final Map<Class<?>, List<NotificationEventHandler<?>>> handlers;
    private final NotificationDispatcher dispatcher;

    public NotificationEventListener(List<NotificationEventHandler<?>> handlers, NotificationDispatcher dispatcher) {
        this.handlers = handlers.stream().collect(Collectors.groupingBy(h -> (Class<?>) h.eventType()));
        this.dispatcher = dispatcher;
    }

    /** İşlem dışında yayınlanan olaylar da (fallbackExecution) hemen işlenir. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onDomainEvent(PayloadApplicationEvent<?> event) {
        Object payload = event.getPayload();
        List<NotificationEventHandler<?>> matching = handlers.getOrDefault(payload.getClass(), List.of());
        if (matching.isEmpty()) {
            log.debug("Bildirimi olmayan olay: {}", payload);
            return;
        }
        for (NotificationEventHandler<?> handler : matching) {
            dispatcher.dispatch(payload.getClass().getSimpleName(), () -> compose(handler, payload));
        }
    }

    @SuppressWarnings("unchecked")
    private static <E> Optional<NotificationMessage> compose(NotificationEventHandler<E> handler, Object payload) {
        return handler.compose((E) payload);
    }
}
