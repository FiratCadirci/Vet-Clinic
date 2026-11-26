package org.codeflow.veterineryproject.notification.push;

/** Push isteği tümden başarısız (HTTP hatası, istek düzeyinde hata, beklenmedik yanıt ya da push kapalı). */
public class PushGatewayException extends RuntimeException {

    public PushGatewayException(String message) {
        super(message);
    }

    public PushGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
