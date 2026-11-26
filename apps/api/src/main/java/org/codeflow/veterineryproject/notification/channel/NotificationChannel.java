package org.codeflow.veterineryproject.notification.channel;

import org.codeflow.veterineryproject.notification.model.NotificationChannelType;
import org.codeflow.veterineryproject.notification.model.NotificationRecipient;
import org.codeflow.veterineryproject.notification.model.OutgoingNotification;

/**
 * Kanal adaptörü (Adapter + Strategy). Bildirim servisi yalnız bu arayüzü bilir (DIP); yeni kanal (ör. SMS)
 * eklemek için bu arayüzü uygulayan bir Spring bileşeni yazmak yeterlidir, mevcut kod değişmez (OCP).
 */
public interface NotificationChannel {

    NotificationChannelType type();

    /** Bu alıcıya bu kanaldan gönderilebilir mi (e-posta adresi var mı, kayıtlı cihazı var mı...). */
    boolean supports(NotificationRecipient recipient);

    /**
     * Bildirimi teslim eder. Hata fırlatırsa gönderim kaydı FAILED olur; iş akışı etkilenmez.
     *
     * @return sağlayıcının mesaj kimliği (yoksa null)
     */
    String deliver(OutgoingNotification notification) throws Exception;
}
