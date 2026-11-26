package org.codeflow.veterineryproject.notification.dto;

/** Hatırlatma görevi sonucu: hatırlatması bu koşuda gönderilen aşı kaydı sayısı. */
public record ReminderRunResponse(int processed) {
}
