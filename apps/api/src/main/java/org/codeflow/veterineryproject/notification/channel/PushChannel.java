package org.codeflow.veterineryproject.notification.channel;

import org.codeflow.veterineryproject.notification.model.NotificationChannelType;
import org.codeflow.veterineryproject.notification.model.NotificationRecipient;
import org.codeflow.veterineryproject.notification.model.OutgoingNotification;
import org.codeflow.veterineryproject.notification.push.PushGateway;
import org.codeflow.veterineryproject.notification.push.PushMessage;
import org.codeflow.veterineryproject.notification.push.PushTicket;
import org.codeflow.veterineryproject.notification.service.PushDeviceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Mobil push kanalı. Bildirimi kullanıcının kayıtlı tüm cihazları için push mesajına çevirir; HTTP ayrıntısı
 * {@link PushGateway}'dedir (DIP), böylece kanal sahte gateway ile test edilebilir.
 * DeviceNotRegistered yanıtı alan token silinir. Hiçbir cihaza iletilemezse hata fırlatır (kayıt FAILED).
 */
@Component
public class PushChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(PushChannel.class);

    private final PushDeviceService devices;
    private final PushGateway gateway;

    public PushChannel(PushDeviceService devices, PushGateway gateway) {
        this.devices = devices;
        this.gateway = gateway;
    }

    @Override
    public NotificationChannelType type() {
        return NotificationChannelType.PUSH;
    }

    /** Kayıtlı cihazı olmayan kullanıcı için push kaydı hiç oluşturulmaz. */
    @Override
    public boolean supports(NotificationRecipient recipient) {
        return recipient.userId() != null && devices.hasDevice(recipient.userId());
    }

    @Override
    public String deliver(OutgoingNotification n) {
        List<String> tokens = devices.tokensOf(n.recipient().userId());
        if (tokens.isEmpty()) {
            throw new IllegalStateException("Kayıtlı cihaz yok.");
        }
        List<PushMessage> messages = tokens.stream().map(t -> PushMessage.of(t, n.title(), n.body(), data(n))).toList();

        int delivered = 0;
        String firstTicketId = null;
        Set<String> errors = new LinkedHashSet<>();
        List<String> unregistered = new ArrayList<>();
        for (int i = 0; i < messages.size(); i += PushGateway.BATCH_LIMIT) {
            List<PushMessage> batch = messages.subList(i, Math.min(messages.size(), i + PushGateway.BATCH_LIMIT));
            try {
                List<PushTicket> tickets = gateway.send(batch);
                for (int j = 0; j < tickets.size() && j < batch.size(); j++) {
                    PushTicket ticket = tickets.get(j);
                    if (ticket.ok()) {
                        delivered++;
                        firstTicketId = firstTicketId == null ? ticket.id() : firstTicketId;
                    } else {
                        errors.add(ticket.errorDescription());
                        if (ticket.deviceNotRegistered()) {
                            unregistered.add(batch.get(j).to());
                        }
                    }
                }
            } catch (RuntimeException ex) {
                errors.add(ex.getMessage());
            }
        }
        if (!unregistered.isEmpty()) {
            devices.removeUnregistered(unregistered);
            log.info("{} geçersiz push token silindi (DeviceNotRegistered)", unregistered.size());
        }
        if (delivered == 0) {
            throw new IllegalStateException("Push iletilemedi: " + String.join("; ", errors));
        }
        if (!errors.isEmpty()) {
            log.warn("Push kısmen iletildi ({}/{}): {}", delivered, messages.size(), String.join("; ", errors));
        }
        return firstTicketId;
    }

    /** Mobil uygulama bildirime dokunulunca bu alanlarla ilgili ekrana gider. */
    private static Map<String, Object> data(OutgoingNotification n) {
        Map<String, Object> data = new HashMap<>();
        data.put("notificationId", n.id().toString());
        data.put("type", n.type().name());
        if (n.animalId() != null) {
            data.put("animalId", n.animalId().toString());
        }
        if (n.appointmentId() != null) {
            data.put("appointmentId", n.appointmentId().toString());
        }
        return data;
    }
}
