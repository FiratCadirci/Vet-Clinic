package org.codeflow.veterineryproject.notification.push;

import java.util.List;

/** {@code app.push.enabled=false} (varsayılan): dışarıya istek atılmaz, push kaydı açıklamasıyla FAILED kalır. */
public class DisabledPushGateway implements PushGateway {

    @Override
    public List<PushTicket> send(List<PushMessage> messages) {
        throw new PushGatewayException("Push gönderimi kapalı (app.push.enabled=false).");
    }
}
