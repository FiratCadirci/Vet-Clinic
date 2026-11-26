package org.codeflow.veterineryproject.notification.service;

import jakarta.persistence.EntityNotFoundException;
import org.codeflow.veterineryproject.common.tenant.TenantContext;
import org.codeflow.veterineryproject.notification.dto.PushDeviceResponse;
import org.codeflow.veterineryproject.notification.dto.RegisterPushDeviceRequest;
import org.codeflow.veterineryproject.notification.repository.PushDeviceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Push cihaz kaydı. register/unregister oturumdaki kullanıcı içindir (mobil uygulama girişte/çıkışta çağırır;
 * hayvan sahibi uç noktası portal modülündedir). Diğer metotlar push kanalı tarafından kullanılır.
 */
@Service
public class PushDeviceService {

    private final PushDeviceRepository repository;

    public PushDeviceService(PushDeviceRepository repository) {
        this.repository = repository;
    }

    /** İdempotent: aynı token tekrar gelirse son görülme güncellenir; başka kullanıcıdaysa bu kullanıcıya taşınır. */
    @Transactional
    public PushDeviceResponse register(RegisterPushDeviceRequest request) {
        TenantContext.ContextValues ctx = TenantContext.require();
        return repository.upsert(ctx.organizationId(), ctx.userId(), request.expoPushToken().trim(),
                request.platform().trim().toUpperCase(Locale.ROOT));
    }

    /** Yalnız kendi cihazı silinebilir; başkasının ya da olmayan token 404. */
    @Transactional
    public void unregister(String expoPushToken) {
        UUID userId = TenantContext.require().userId();
        if (repository.deleteOwn(userId, expoPushToken) == 0) {
            throw new EntityNotFoundException("Cihaz bulunamadı.");
        }
    }

    @Transactional(readOnly = true)
    public boolean hasDevice(UUID userId) {
        return repository.existsForUser(userId);
    }

    @Transactional(readOnly = true)
    public List<String> tokensOf(UUID userId) {
        return repository.findTokens(userId);
    }

    /** Expo DeviceNotRegistered dediği token'lar (uygulama silinmiş / izin kaldırılmış) kalıcı olarak silinir. */
    @Transactional
    public int removeUnregistered(List<String> tokens) {
        return repository.deleteTokens(tokens);
    }
}
