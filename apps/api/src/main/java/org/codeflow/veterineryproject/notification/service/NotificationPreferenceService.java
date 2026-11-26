package org.codeflow.veterineryproject.notification.service;

import org.codeflow.veterineryproject.common.tenant.TenantContext;
import org.codeflow.veterineryproject.notification.dto.PreferenceResponse;
import org.codeflow.veterineryproject.notification.dto.PreferenceResponse.ChannelPreference;
import org.codeflow.veterineryproject.notification.dto.UpdatePreferencesRequest;
import org.codeflow.veterineryproject.notification.model.NotificationChannelType;
import org.codeflow.veterineryproject.notification.model.NotificationType;
import org.codeflow.veterineryproject.notification.repository.NotificationPreferenceRepository;
import org.codeflow.veterineryproject.notification.repository.NotificationPreferenceRepository.StoredPreference;
import org.codeflow.veterineryproject.notification.repository.NotificationRecipientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Oturumdaki kullanıcının olay türü × kanal bildirim tercihleri (FG-I2). Kayıtlı tercih yoksa kanal açıktır;
 * kapalı kanala gönderim yapılmaz, gönderim kaydına SKIPPED yazılır.
 */
@Service
public class NotificationPreferenceService {

    private final NotificationPreferenceRepository repository;
    private final NotificationRecipientRepository recipients;

    public NotificationPreferenceService(NotificationPreferenceRepository repository, NotificationRecipientRepository recipients) {
        this.repository = repository;
        this.recipients = recipients;
    }

    @Transactional(readOnly = true)
    public boolean isEnabled(UUID userId, NotificationType type, NotificationChannelType channel) {
        if (!type.configurable()) {
            return true;
        }
        return repository.findEnabled(userId, type.name(), channel.name()).orElse(true);
    }

    /** Personel stok/davet türlerini, hayvan sahibi randevu/aşı türlerini görür. */
    @Transactional(readOnly = true)
    public List<PreferenceResponse> list() {
        UUID userId = TenantContext.require().userId();
        NotificationType.Audience audience = recipients.isClientAccount(userId)
                ? NotificationType.Audience.OWNER : NotificationType.Audience.STAFF;
        Map<String, Boolean> stored = repository.findByUser(userId).stream()
                .collect(Collectors.toMap(p -> key(p.type(), p.channel()), StoredPreference::enabled));
        return Arrays.stream(NotificationType.values())
                .filter(t -> t.configurable() && t.audience() == audience)
                .map(t -> new PreferenceResponse(t.name(), t.label(), t.channels().stream()
                        .map(c -> new ChannelPreference(c.name(), stored.getOrDefault(key(t.name(), c.name()), true)))
                        .toList()))
                .toList();
    }

    @Transactional
    public List<PreferenceResponse> update(UpdatePreferencesRequest request) {
        TenantContext.ContextValues ctx = TenantContext.require();
        for (UpdatePreferencesRequest.Item item : request.preferences()) {
            NotificationType type = NotificationType.parse(item.type().trim().toUpperCase(Locale.ROOT))
                    .filter(NotificationType::configurable)
                    .orElseThrow(() -> new IllegalArgumentException("Bilinmeyen ya da tercih edilemeyen bildirim türü: " + item.type()));
            NotificationChannelType channel = Arrays.stream(NotificationChannelType.values())
                    .filter(c -> c.name().equalsIgnoreCase(item.channel().trim()) && type.supports(c))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(type.label() + " için geçersiz kanal: " + item.channel()));
            repository.upsert(ctx.organizationId(), ctx.userId(), type.name(), channel.name(), item.enabled());
        }
        return list();
    }

    private static String key(String type, String channel) {
        return type + ":" + channel;
    }
}
