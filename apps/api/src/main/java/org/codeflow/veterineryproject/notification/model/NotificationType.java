package org.codeflow.veterineryproject.notification.model;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import static org.codeflow.veterineryproject.notification.model.NotificationChannelType.EMAIL;
import static org.codeflow.veterineryproject.notification.model.NotificationChannelType.IN_APP;
import static org.codeflow.veterineryproject.notification.model.NotificationChannelType.PUSH;

/**
 * Bildirim türleri kataloğu: Türkçe etiket, hangi kanallardan gidebileceği, kime yönelik olduğu ve kullanıcının
 * tercihle kapatıp kapatamayacağı. Yeni tür = yeni sabit + o türü üreten {@code NotificationEventHandler}.
 */
public enum NotificationType {
    APPOINTMENT_CONFIRMED("Randevu onayı", Audience.OWNER, true, IN_APP, EMAIL, PUSH),
    APPOINTMENT_CANCELLED("Randevu iptali", Audience.OWNER, true, IN_APP, EMAIL, PUSH),
    APPOINTMENT_RESCHEDULED("Randevu değişikliği", Audience.OWNER, true, IN_APP, EMAIL, PUSH),
    VACCINE_RECORDED("Aşı kaydı", Audience.OWNER, true, IN_APP, EMAIL, PUSH),
    VACCINE_DUE("Aşı hatırlatması", Audience.OWNER, true, IN_APP, EMAIL, PUSH),
    INVOICE_ISSUED("Fatura", Audience.OWNER, true, IN_APP, EMAIL, PUSH),
    STOCK_LOW("Kritik stok uyarısı", Audience.STAFF, true, IN_APP),
    /** Davet edilenin henüz hesabı yoktur; yalnız e-posta, tercih edilemez. */
    STAFF_INVITED("Personel daveti", Audience.STAFF, false, EMAIL);

    /** Bildirimin hedef kitlesi; tercih ekranında yalnız kullanıcıyı ilgilendiren türler listelenir. */
    public enum Audience { STAFF, OWNER }

    private final String label;
    private final Audience audience;
    private final boolean configurable;
    private final Set<NotificationChannelType> channels;

    NotificationType(String label, Audience audience, boolean configurable, NotificationChannelType first,
                     NotificationChannelType... rest) {
        this.label = label;
        this.audience = audience;
        this.configurable = configurable;
        this.channels = EnumSet.of(first, rest);
    }

    public String label() {
        return label;
    }

    public Audience audience() {
        return audience;
    }

    public boolean configurable() {
        return configurable;
    }

    public Set<NotificationChannelType> channels() {
        return channels;
    }

    public boolean supports(NotificationChannelType channel) {
        return channels.contains(channel);
    }

    public static Optional<NotificationType> parse(String code) {
        return Arrays.stream(values()).filter(t -> t.name().equals(code)).findFirst();
    }
}
