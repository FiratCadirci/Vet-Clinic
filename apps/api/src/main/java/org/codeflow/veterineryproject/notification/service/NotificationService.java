package org.codeflow.veterineryproject.notification.service;

import org.codeflow.veterineryproject.common.tenant.TenantContext;
import org.codeflow.veterineryproject.notification.channel.NotificationChannel;
import org.codeflow.veterineryproject.notification.channel.NotificationChannelRegistry;
import org.codeflow.veterineryproject.notification.model.DeliveryReport;
import org.codeflow.veterineryproject.notification.model.DeliveryStatus;
import org.codeflow.veterineryproject.notification.model.NotificationChannelType;
import org.codeflow.veterineryproject.notification.model.NotificationMessage;
import org.codeflow.veterineryproject.notification.model.NotificationRecipient;
import org.codeflow.veterineryproject.notification.model.OutgoingNotification;
import org.codeflow.veterineryproject.notification.repository.NotificationJdbcRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Bildirim gönderim hattı: alıcı × kanal için tercihe bakar, her denemeyi gönderim kaydına yazar
 * (PENDING / SKIPPED), kayıt commit edildikten sonra kanal adaptörüne teslim eder ve sonucu SENT / FAILED
 * olarak işler. Kanal hatası yakalanır; çağırana (ve iş işlemine) asla yansımaz (FG-I4).
 * Organizasyon, çağıran iş parçacığındaki {@link TenantContext}'ten alınır (istek, olay ya da zamanlanmış görev).
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    static final String SKIPPED_REASON = "Kullanıcı tercihi kapalı.";

    private final NotificationChannelRegistry channels;
    private final NotificationJdbcRepository outbox;
    private final NotificationPreferenceService preferences;
    private final TransactionTemplate tx;

    public NotificationService(NotificationChannelRegistry channels, NotificationJdbcRepository outbox,
                               NotificationPreferenceService preferences, PlatformTransactionManager transactionManager) {
        this.channels = channels;
        this.outbox = outbox;
        this.preferences = preferences;
        this.tx = new TransactionTemplate(transactionManager);
        // Gönderim kaydı, kanal teslimi başlamadan kendi işleminde commit edilir.
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    private record Pending(NotificationChannel channel, OutgoingNotification notification) {
    }

    public List<DeliveryReport> send(NotificationMessage message) {
        UUID organizationId = TenantContext.get().map(TenantContext.ContextValues::organizationId)
                .orElseThrow(() -> new IllegalStateException("Bildirim için organizasyon bağlamı gerekli."));
        List<DeliveryReport> reports = new ArrayList<>();
        List<Pending> pending = new ArrayList<>();
        tx.executeWithoutResult(status -> record(organizationId, message, reports, pending));
        for (Pending p : pending) {
            reports.add(deliver(p));
        }
        return reports;
    }

    private void record(UUID organizationId, NotificationMessage message, List<DeliveryReport> reports, List<Pending> pending) {
        Set<String> seen = new HashSet<>();
        for (NotificationRecipient recipient : message.recipients()) {
            if (recipient.identity().isEmpty() || !seen.add(recipient.identity())) {
                continue;
            }
            for (NotificationChannel channel : channels.channelsFor(message.type())) {
                if (!channel.supports(recipient)) {
                    continue;
                }
                boolean enabled = recipient.userId() == null
                        || preferences.isEnabled(recipient.userId(), message.type(), channel.type());
                DeliveryStatus status = enabled ? DeliveryStatus.PENDING : DeliveryStatus.SKIPPED;
                UUID id = outbox.insert(organizationId, message.type(), channel.type(), recipient, message.title(),
                        message.bodyToStore(), message.animalId(), message.appointmentId(), status,
                        enabled ? null : SKIPPED_REASON);
                if (enabled) {
                    pending.add(new Pending(channel, new OutgoingNotification(id, organizationId, message.type(),
                            channel.type(), recipient, message.title(), message.body(), message.animalId(),
                            message.appointmentId())));
                } else {
                    reports.add(new DeliveryReport(id, channel.type(), address(channel.type(), recipient), DeliveryStatus.SKIPPED));
                }
            }
        }
    }

    private DeliveryReport deliver(Pending p) {
        OutgoingNotification n = p.notification();
        String address = address(n.channel(), n.recipient());
        String providerId;
        try {
            providerId = p.channel().deliver(n);
        } catch (Exception ex) {
            String reason = describe(ex);
            log.warn("{} bildirimi gönderilemedi ({}): {}", n.channel(), n.type(), reason);
            mark(() -> outbox.markFailed(n.id(), reason), n);
            return new DeliveryReport(n.id(), n.channel(), address, DeliveryStatus.FAILED);
        }
        mark(() -> outbox.markSent(n.id(), providerId), n);
        return new DeliveryReport(n.id(), n.channel(), address, DeliveryStatus.SENT);
    }

    /** Kayıt güncellenemese bile (ör. veritabanı kısa süre erişilemez) hata dışarı taşmaz. */
    private void mark(Runnable update, OutgoingNotification n) {
        try {
            tx.executeWithoutResult(status -> update.run());
        } catch (RuntimeException ex) {
            log.error("Gönderim kaydı güncellenemedi id={}: {}", n.id(), ex.getMessage());
        }
    }

    private static String address(NotificationChannelType channel, NotificationRecipient recipient) {
        return channel == NotificationChannelType.EMAIL ? recipient.email() : String.valueOf(recipient.userId());
    }

    private static String describe(Exception ex) {
        Throwable root = ex;
        StringBuilder message = new StringBuilder(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        if (root != ex && root.getMessage() != null && !message.toString().contains(root.getMessage())) {
            message.append(" (").append(root.getMessage()).append(')');
        }
        return message.toString();
    }
}
