package org.codeflow.veterineryproject.notification;

import org.codeflow.veterineryproject.common.event.DomainEvents.StaffInvitedEvent;
import org.codeflow.veterineryproject.notification.event.StaffInvitedNotificationHandler;
import org.codeflow.veterineryproject.notification.model.NotificationMessage;
import org.codeflow.veterineryproject.notification.model.NotificationType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Davet e-postası içeriği: bağlantı biçimi, klinik adı, Türkçe metin ve gönderim kaydında maskelenmiş token. */
class StaffInvitedNotificationHandlerTest {

    private final StaffInvitedNotificationHandler handler = new StaffInvitedNotificationHandler("https://panel.vetclinic.test/");

    @Test
    void composesEmailOnlyInviteWithEncodedAcceptLink() {
        NotificationMessage message = handler.compose(new StaffInvitedEvent(UUID.randomUUID(), UUID.randomUUID(),
                "yeni.hekim@test.com", "a b+c/d", "Pati Kliniği", UUID.randomUUID())).orElseThrow();

        assertThat(message.type()).isEqualTo(NotificationType.STAFF_INVITED);
        assertThat(message.title()).isEqualTo("Pati Kliniği sizi VetClinic'e davet etti");
        assertThat(message.body()).contains("https://panel.vetclinic.test/accept-invite?token=a+b%2Bc%2Fd", "Pati Kliniği", "davet etti");
        assertThat(message.bodyToStore()).contains("accept-invite?token=***").doesNotContain("a+b%2Bc%2Fd");
        assertThat(message.recipients()).singleElement().satisfies(r -> {
            assertThat(r.email()).isEqualTo("yeni.hekim@test.com");
            assertThat(r.userId()).isNull();
        });
    }

    @Test
    void missingEmailOrTokenProducesNothing() {
        assertThat(handler.compose(new StaffInvitedEvent(UUID.randomUUID(), UUID.randomUUID(), " ", "t", "K", null))).isEmpty();
        assertThat(handler.compose(new StaffInvitedEvent(UUID.randomUUID(), UUID.randomUUID(), "a@b.com", null, "K", null))).isEmpty();
    }
}
