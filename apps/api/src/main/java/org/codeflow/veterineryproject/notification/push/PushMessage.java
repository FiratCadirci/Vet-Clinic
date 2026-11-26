package org.codeflow.veterineryproject.notification.push;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/** Expo push mesajı (https://docs.expo.dev/push-notifications/sending-notifications/). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PushMessage(String to, String title, String body, Map<String, Object> data, String sound,
                          String channelId, String priority) {

    /** Mobil uygulamanın oluşturduğu Android "default" kanalı, sesli ve yüksek öncelikli. */
    public static PushMessage of(String to, String title, String body, Map<String, Object> data) {
        return new PushMessage(to, title, body, data, "default", "default", "high");
    }
}
