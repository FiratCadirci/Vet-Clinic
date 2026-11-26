package org.codeflow.veterineryproject.notification.event;

import org.codeflow.veterineryproject.notification.model.NotificationMessage;

import java.util.Optional;

/**
 * Bir domain olayını bildirime çeviren strateji: alıcıları çözer, Türkçe metni üretir. Yeni bildirim türü için
 * bu arayüzü uygulayan bir Spring bileşeni yazılır; {@link NotificationEventListener} onu kendiliğinden bulur.
 *
 * @param <E> dinlenen olay (common.event.DomainEvents içindeki record)
 */
public interface NotificationEventHandler<E> {

    Class<E> eventType();

    /** Gönderilecek bir şey yoksa (alıcı yok, ilgisiz durum değişikliği) boş döner. */
    Optional<NotificationMessage> compose(E event);
}
