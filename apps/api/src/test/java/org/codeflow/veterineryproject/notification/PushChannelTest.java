package org.codeflow.veterineryproject.notification;

import org.codeflow.veterineryproject.notification.channel.PushChannel;
import org.codeflow.veterineryproject.notification.model.NotificationChannelType;
import org.codeflow.veterineryproject.notification.model.NotificationRecipient;
import org.codeflow.veterineryproject.notification.model.NotificationType;
import org.codeflow.veterineryproject.notification.model.OutgoingNotification;
import org.codeflow.veterineryproject.notification.push.DisabledPushGateway;
import org.codeflow.veterineryproject.notification.push.PushGateway;
import org.codeflow.veterineryproject.notification.push.PushMessage;
import org.codeflow.veterineryproject.notification.push.PushTicket;
import org.codeflow.veterineryproject.notification.service.PushDeviceService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Push kanalı (Adapter: bildirim → Expo mesajı) sahte gateway ile; DB ve ağ yok. */
class PushChannelTest {

    private static final UUID USER = UUID.randomUUID();

    private final PushDeviceService devices = mock(PushDeviceService.class);

    @Test
    void mapsNotificationToMessagesForEveryDevice_inBatchesOfAtMost100() {
        List<String> tokens = IntStream.range(0, 150).mapToObj(i -> "ExponentPushToken[t" + i + "]").toList();
        when(devices.tokensOf(USER)).thenReturn(tokens);
        RecordingGateway gateway = new RecordingGateway();

        String ticket = new PushChannel(devices, gateway).deliver(notification());

        assertThat(gateway.batches).extracting(List::size).containsExactly(100, 50);
        PushMessage first = gateway.batches.get(0).get(0);
        assertThat(first.title()).isEqualTo("Randevunuz onaylandı");
        assertThat(first.body()).isEqualTo("Pamuk için randevunuz onaylandı.");
        assertThat(first.data()).containsEntry("type", "APPOINTMENT_CONFIRMED").containsKey("notificationId").containsKey("appointmentId");
        assertThat(first.sound()).isEqualTo("default");
        assertThat(ticket).isEqualTo("ticket-ExponentPushToken[t0]");
        verify(devices, never()).removeUnregistered(anyList());
    }

    @Test
    void deviceNotRegistered_tokenIsRemoved_partialSuccessStillCountsAsSent() {
        when(devices.tokensOf(USER)).thenReturn(List.of("ExponentPushToken[ok]", "ExponentPushToken[gone]"));
        RecordingGateway gateway = new RecordingGateway();
        gateway.errorFor = "ExponentPushToken[gone]";

        String ticket = new PushChannel(devices, gateway).deliver(notification());

        assertThat(ticket).isEqualTo("ticket-ExponentPushToken[ok]");
        verify(devices).removeUnregistered(List.of("ExponentPushToken[gone]"));
    }

    @Test
    void noDeviceReached_throwsSoTheDeliveryIsMarkedFailed() {
        when(devices.tokensOf(USER)).thenReturn(List.of("ExponentPushToken[gone]"));
        RecordingGateway gateway = new RecordingGateway();
        gateway.errorFor = "ExponentPushToken[gone]";

        assertThatThrownBy(() -> new PushChannel(devices, gateway).deliver(notification()))
                .hasMessageContaining("Push iletilemedi").hasMessageContaining("DeviceNotRegistered");
        verify(devices).removeUnregistered(List.of("ExponentPushToken[gone]"));

        assertThatThrownBy(() -> new PushChannel(devices, new DisabledPushGateway()).deliver(notification()))
                .hasMessageContaining("Push gönderimi kapalı");
    }

    @Test
    void supportsOnlyUsersWithARegisteredDevice() {
        when(devices.hasDevice(USER)).thenReturn(true);
        PushChannel channel = new PushChannel(devices, new RecordingGateway());

        assertThat(channel.type()).isEqualTo(NotificationChannelType.PUSH);
        assertThat(channel.supports(new NotificationRecipient(USER, null, null, null))).isTrue();
        assertThat(channel.supports(NotificationRecipient.emailOnly("a@b.com"))).isFalse();
        assertThat(channel.supports(new NotificationRecipient(UUID.randomUUID(), null, null, null))).isFalse();
    }

    private static OutgoingNotification notification() {
        return new OutgoingNotification(UUID.randomUUID(), UUID.randomUUID(), NotificationType.APPOINTMENT_CONFIRMED,
                NotificationChannelType.PUSH, new NotificationRecipient(USER, null, null, "Sahip"), "Randevunuz onaylandı",
                "Pamuk için randevunuz onaylandı.", UUID.randomUUID(), UUID.randomUUID());
    }

    private static final class RecordingGateway implements PushGateway {
        final List<List<PushMessage>> batches = new ArrayList<>();
        String errorFor;

        @Override
        public List<PushTicket> send(List<PushMessage> messages) {
            batches.add(List.copyOf(messages));
            return messages.stream().map(m -> m.to().equals(errorFor)
                    ? PushTicket.error("not registered", PushTicket.DEVICE_NOT_REGISTERED)
                    : PushTicket.ok("ticket-" + m.to())).toList();
        }
    }
}
