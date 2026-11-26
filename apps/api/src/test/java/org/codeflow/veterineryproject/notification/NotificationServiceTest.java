package org.codeflow.veterineryproject.notification;

import org.codeflow.veterineryproject.common.tenant.TenantContext;
import org.codeflow.veterineryproject.notification.channel.NotificationChannel;
import org.codeflow.veterineryproject.notification.channel.NotificationChannelRegistry;
import org.codeflow.veterineryproject.notification.model.DeliveryReport;
import org.codeflow.veterineryproject.notification.model.DeliveryStatus;
import org.codeflow.veterineryproject.notification.model.NotificationChannelType;
import org.codeflow.veterineryproject.notification.model.NotificationMessage;
import org.codeflow.veterineryproject.notification.model.NotificationRecipient;
import org.codeflow.veterineryproject.notification.model.NotificationType;
import org.codeflow.veterineryproject.notification.model.OutgoingNotification;
import org.codeflow.veterineryproject.notification.repository.NotificationJdbcRepository;
import org.codeflow.veterineryproject.notification.service.NotificationPreferenceService;
import org.codeflow.veterineryproject.notification.service.NotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Gönderim hattı (Strategy: kanal seçimi, tercih, hata yalıtımı) birim testleri; DB ve SMTP yok. */
class NotificationServiceTest {

    private static final UUID ORG = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();

    private final NotificationJdbcRepository outbox = mock(NotificationJdbcRepository.class);
    private final NotificationPreferenceService preferences = mock(NotificationPreferenceService.class);
    private final RecordingChannel inApp = new RecordingChannel(NotificationChannelType.IN_APP, r -> r.userId() != null, false);
    private final RecordingChannel email = new RecordingChannel(NotificationChannelType.EMAIL, NotificationRecipient::hasEmail, true);
    private NotificationService service;

    @BeforeEach
    void setUp() {
        PlatformTransactionManager tm = mock(PlatformTransactionManager.class);
        when(tm.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(outbox.insert(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenAnswer(i -> UUID.randomUUID());
        when(preferences.isEnabled(any(), any(), any())).thenReturn(true);
        service = new NotificationService(new NotificationChannelRegistry(List.of(email, inApp)), outbox, preferences, tm);
        TenantContext.set(ORG, USER, null);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void failingChannelIsRecordedAsFailed_otherChannelsStillDeliver_noExceptionEscapes() {
        NotificationRecipient owner = new NotificationRecipient(USER, null, "sahip@test.com", "Sahip");

        List<DeliveryReport> reports = service.send(message(NotificationType.APPOINTMENT_CONFIRMED, owner));

        assertThat(reports).extracting(DeliveryReport::channel, DeliveryReport::status)
                .containsExactlyInAnyOrder(org.assertj.core.groups.Tuple.tuple(NotificationChannelType.IN_APP, DeliveryStatus.SENT),
                        org.assertj.core.groups.Tuple.tuple(NotificationChannelType.EMAIL, DeliveryStatus.FAILED));
        verify(outbox).markFailed(any(), eq("SMTP kapalı"));
        verify(outbox).markSent(any(), isNull());
        assertThat(inApp.delivered).hasSize(1);
    }

    @Test
    void disabledPreference_isSkippedAndNeverDelivered() {
        when(preferences.isEnabled(USER, NotificationType.VACCINE_DUE, NotificationChannelType.IN_APP)).thenReturn(false);
        NotificationRecipient owner = new NotificationRecipient(USER, null, null, "Sahip");

        List<DeliveryReport> reports = service.send(message(NotificationType.VACCINE_DUE, owner));

        assertThat(reports).singleElement().extracting(DeliveryReport::status).isEqualTo(DeliveryStatus.SKIPPED);
        assertThat(inApp.delivered).isEmpty();
        verify(outbox).insert(eq(ORG), eq(NotificationType.VACCINE_DUE), eq(NotificationChannelType.IN_APP), eq(owner), anyString(),
                anyString(), any(), any(), eq(DeliveryStatus.SKIPPED), eq("Kullanıcı tercihi kapalı."));
    }

    @Test
    void typeRestrictsChannels_unsupportedRecipientGetsNoRow_duplicateRecipientsAreMerged() {
        NotificationRecipient staff = NotificationRecipient.staff(USER, "Stok", "stok@test.com");

        List<DeliveryReport> reports = service.send(message(NotificationType.STOCK_LOW, staff, staff));
        assertThat(reports).singleElement().extracting(DeliveryReport::channel).isEqualTo(NotificationChannelType.IN_APP);
        assertThat(email.delivered).isEmpty();

        List<DeliveryReport> invite = service.send(message(NotificationType.STAFF_INVITED, NotificationRecipient.emailOnly("yeni@test.com")));
        assertThat(invite).singleElement().extracting(DeliveryReport::channel).isEqualTo(NotificationChannelType.EMAIL);
        verify(preferences, never()).isEnabled(isNull(), any(), any());
    }

    @Test
    void storedBodyIsUsedForTheLog_realBodyGoesToTheChannel() {
        NotificationMessage secret = new NotificationMessage(NotificationType.STOCK_LOW, "Başlık", "gizli-bağlantı", "maskeli",
                null, null, List.of(NotificationRecipient.staff(USER, "Stok", null)));

        service.send(secret);

        verify(outbox).insert(any(), any(), any(), any(), any(), eq("maskeli"), any(), any(), any(), any());
        assertThat(inApp.delivered).singleElement().extracting(OutgoingNotification::body).isEqualTo("gizli-bağlantı");
    }

    private static NotificationMessage message(NotificationType type, NotificationRecipient... recipients) {
        return NotificationMessage.of(type, "Başlık", "Metin", null, null, List.of(recipients));
    }

    /** Sahte kanal: teslim edilenleri saklar, istenirse SMTP kapalıymış gibi hata fırlatır. */
    private static final class RecordingChannel implements NotificationChannel {
        private final NotificationChannelType type;
        private final Predicate<NotificationRecipient> supports;
        private final boolean failing;
        private final List<OutgoingNotification> delivered = new ArrayList<>();

        RecordingChannel(NotificationChannelType type, Predicate<NotificationRecipient> supports, boolean failing) {
            this.type = type;
            this.supports = supports;
            this.failing = failing;
        }

        @Override
        public NotificationChannelType type() {
            return type;
        }

        @Override
        public boolean supports(NotificationRecipient recipient) {
            return supports.test(recipient);
        }

        @Override
        public String deliver(OutgoingNotification notification) {
            if (failing) {
                throw new IllegalStateException("SMTP kapalı");
            }
            delivered.add(notification);
            return null;
        }
    }
}
