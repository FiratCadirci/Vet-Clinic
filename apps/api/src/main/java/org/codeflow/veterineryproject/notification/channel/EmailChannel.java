package org.codeflow.veterineryproject.notification.channel;

import org.codeflow.veterineryproject.notification.model.NotificationChannelType;
import org.codeflow.veterineryproject.notification.model.NotificationRecipient;
import org.codeflow.veterineryproject.notification.model.OutgoingNotification;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** E-posta kanalı: SMTP ({@link JavaMailSender}; yerelde Mailpit). Gönderen adres {@code app.mail-from}. */
@Component
public class EmailChannel implements NotificationChannel {

    private final JavaMailSender mailSender;
    private final String from;

    public EmailChannel(JavaMailSender mailSender, @Value("${app.mail-from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public NotificationChannelType type() {
        return NotificationChannelType.EMAIL;
    }

    @Override
    public boolean supports(NotificationRecipient recipient) {
        return recipient.hasEmail();
    }

    @Override
    public String deliver(OutgoingNotification notification) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(from);
        mail.setTo(notification.recipient().email().trim());
        mail.setSubject(notification.title());
        mail.setText(notification.body() + "\n\n— VetClinic");
        mailSender.send(mail);
        return null;
    }
}
