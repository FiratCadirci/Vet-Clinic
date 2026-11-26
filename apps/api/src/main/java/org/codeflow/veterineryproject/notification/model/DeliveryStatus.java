package org.codeflow.veterineryproject.notification.model;

/** Gönderim kaydının durumu: SKIPPED, kullanıcı tercihi kapalı olduğu için kanala hiç gönderilmeyendir. */
public enum DeliveryStatus {
    PENDING, SENT, FAILED, SKIPPED
}
