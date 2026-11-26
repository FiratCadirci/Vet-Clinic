package org.codeflow.veterineryproject.notification.event;

import org.codeflow.veterineryproject.common.event.DomainEvents.StaffInvitedEvent;
import org.codeflow.veterineryproject.notification.model.NotificationMessage;
import org.codeflow.veterineryproject.notification.model.NotificationRecipient;
import org.codeflow.veterineryproject.notification.model.NotificationType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/** Personel daveti: davet edilenin henüz hesabı yok, yalnız e-posta gider. Bağlantı gönderim kaydına maskelenir. */
@Component
public class StaffInvitedNotificationHandler implements NotificationEventHandler<StaffInvitedEvent> {

    private final String webUrl;

    public StaffInvitedNotificationHandler(@Value("${app.web-url}") String webUrl) {
        this.webUrl = webUrl.endsWith("/") ? webUrl.substring(0, webUrl.length() - 1) : webUrl;
    }

    @Override
    public Class<StaffInvitedEvent> eventType() {
        return StaffInvitedEvent.class;
    }

    @Override
    public Optional<NotificationMessage> compose(StaffInvitedEvent event) {
        if (event.email() == null || event.email().isBlank() || event.inviteToken() == null) {
            return Optional.empty();
        }
        String clinic = NotificationTexts.orDefault(event.organizationName(), "Kliniğiniz");
        String link = inviteLink(event.inviteToken());
        String title = clinic + " sizi VetClinic'e davet etti";
        String body = template(clinic, link);
        String stored = template(clinic, webUrl + "/accept-invite?token=***");
        return Optional.of(new NotificationMessage(NotificationType.STAFF_INVITED, title, body, stored, null, null,
                List.of(NotificationRecipient.emailOnly(event.email()))));
    }

    String inviteLink(String token) {
        return webUrl + "/accept-invite?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }

    private static String template(String clinic, String link) {
        return """
                Merhaba,

                %s sizi VetClinic klinik yönetim sistemine personel olarak davet etti.
                Hesabınızı oluşturmak ve daveti kabul etmek için aşağıdaki bağlantıyı açın:

                %s

                Bu daveti beklemiyorsanız bu e-postayı yok sayabilirsiniz.""".formatted(clinic, link);
    }
}
