package org.codeflow.veterineryproject.notification.channel;

import org.codeflow.veterineryproject.notification.model.NotificationChannelType;
import org.codeflow.veterineryproject.notification.model.NotificationRecipient;
import org.codeflow.veterineryproject.notification.model.OutgoingNotification;
import org.springframework.stereotype.Component;

/** Uygulama içi bildirim: gönderim kaydındaki IN_APP satırı kullanıcının gelen kutusudur, ek teslim adımı yoktur. */
@Component
public class InAppChannel implements NotificationChannel {

    @Override
    public NotificationChannelType type() {
        return NotificationChannelType.IN_APP;
    }

    @Override
    public boolean supports(NotificationRecipient recipient) {
        return recipient.userId() != null;
    }

    @Override
    public String deliver(OutgoingNotification notification) {
        return null;
    }
}
