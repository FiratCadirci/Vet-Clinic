package org.codeflow.veterineryproject.notification.push;

import java.util.List;

/**
 * Push sağlayıcısına giden dar arayüz (Dependency Inversion). {@code PushChannel} HTTP'yi bilmez; üretimde
 * {@link ExpoPushGateway}, push kapalıyken {@link DisabledPushGateway}, testlerde sahte uygulama verilir.
 */
public interface PushGateway {

    /** Expo Push API'nin tek istekte kabul ettiği en fazla mesaj sayısı. */
    int BATCH_LIMIT = 100;

    /**
     * @param messages en fazla {@link #BATCH_LIMIT} mesaj
     * @return mesajlarla aynı sırada ticket'lar; istek tümden başarısızsa {@link PushGatewayException}
     */
    List<PushTicket> send(List<PushMessage> messages);
}
