package org.codeflow.veterineryproject.notification.push;

/**
 * Gönderilen her mesaj için dönen "push ticket".
 *
 * @param error mesaj düzeyindeki hata kodu (ör. DeviceNotRegistered, MessageTooBig)
 */
public record PushTicket(String status, String id, String message, String error) {

    public static final String DEVICE_NOT_REGISTERED = "DeviceNotRegistered";

    public static PushTicket ok(String id) {
        return new PushTicket("ok", id, null, null);
    }

    public static PushTicket error(String message, String error) {
        return new PushTicket("error", null, message, error);
    }

    public boolean ok() {
        return "ok".equals(status);
    }

    public boolean deviceNotRegistered() {
        return DEVICE_NOT_REGISTERED.equals(error);
    }

    public String errorDescription() {
        if (error != null && message != null) {
            return error + ": " + message;
        }
        return error != null ? error : String.valueOf(message);
    }
}
