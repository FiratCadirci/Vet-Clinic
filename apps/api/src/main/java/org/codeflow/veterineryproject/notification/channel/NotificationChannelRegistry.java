package org.codeflow.veterineryproject.notification.channel;

import org.codeflow.veterineryproject.notification.model.NotificationType;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/** Spring'in bulduğu tüm kanal adaptörleri; bir bildirim türünün izin verdiği kanallar buradan seçilir. */
@Component
public class NotificationChannelRegistry {

    private final List<NotificationChannel> channels;

    public NotificationChannelRegistry(List<NotificationChannel> channels) {
        this.channels = channels.stream().sorted(Comparator.comparing(NotificationChannel::type)).toList();
    }

    public List<NotificationChannel> channelsFor(NotificationType type) {
        return channels.stream().filter(c -> type.supports(c.type())).toList();
    }
}
