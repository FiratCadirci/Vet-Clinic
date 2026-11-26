package org.codeflow.veterineryproject.notification.model;

import java.util.UUID;

/**
 * Bildirimin alıcısı. userId: uygulama hesabı (uygulama içi + push + tercihler), email: e-posta kanalı,
 * clientId: alıcı hayvan sahibiyse gönderim kaydında müşteriye bağ.
 */
public record NotificationRecipient(UUID userId, UUID clientId, String email, String displayName) {

    public static NotificationRecipient staff(UUID userId, String displayName, String email) {
        return new NotificationRecipient(userId, null, email, displayName);
    }

    public static NotificationRecipient emailOnly(String email) {
        return new NotificationRecipient(null, null, email, null);
    }

    public boolean hasEmail() {
        return email != null && !email.isBlank();
    }

    /** Aynı kişiye aynı bildirimin iki kez gitmemesi için kimlik. */
    public String identity() {
        return userId != null ? userId.toString() : (email == null ? "" : email.trim().toLowerCase());
    }
}
