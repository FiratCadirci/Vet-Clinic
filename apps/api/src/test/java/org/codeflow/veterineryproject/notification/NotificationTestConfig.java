package org.codeflow.veterineryproject.notification;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.codeflow.veterineryproject.notification.push.PushGateway;
import org.codeflow.veterineryproject.notification.push.PushMessage;
import org.codeflow.veterineryproject.notification.push.PushTicket;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessagePreparator;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** CI'da SMTP ve Expo yoktur: e-posta ve push kanalları bellekteki sahte uygulamalarla doğrulanır. */
@TestConfiguration
public class NotificationTestConfig {

    @Bean
    @Primary
    public FakeMailSender fakeMailSender() {
        return new FakeMailSender();
    }

    @Bean
    @Primary
    public FakePushGateway fakePushGateway() {
        return new FakePushGateway();
    }

    /** Gönderilen e-postaları saklar; {@link #failing} açıkken SMTP kapalıymış gibi hata fırlatır. */
    public static class FakeMailSender implements JavaMailSender {
        public final List<SimpleMailMessage> sent = new CopyOnWriteArrayList<>();
        public volatile boolean failing;

        public List<SimpleMailMessage> to(String address) {
            return sent.stream().filter(m -> m.getTo() != null && List.of(m.getTo()).contains(address)).toList();
        }

        @Override
        public void send(SimpleMailMessage... messages) {
            if (failing) {
                throw new MailSendException("Mail server connection failed (localhost:1025 Connection refused)");
            }
            sent.addAll(List.of(messages));
        }

        @Override
        public MimeMessage createMimeMessage() {
            return new MimeMessage(Session.getInstance(new Properties()));
        }

        @Override
        public MimeMessage createMimeMessage(java.io.InputStream contentStream) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void send(MimeMessage... mimeMessages) {
            throw new UnsupportedOperationException("Testte yalnız SimpleMailMessage beklenir");
        }

        @Override
        public void send(MimeMessagePreparator... mimeMessagePreparators) {
            throw new UnsupportedOperationException("Testte yalnız SimpleMailMessage beklenir");
        }
    }

    /** Gönderilen push mesajlarını saklar; token bazında hata ticket'ı tanımlanabilir. */
    public static class FakePushGateway implements PushGateway {
        public final List<PushMessage> sent = new CopyOnWriteArrayList<>();
        public final Map<String, PushTicket> errors = new ConcurrentHashMap<>();

        public List<PushMessage> to(String token) {
            return sent.stream().filter(m -> m.to().equals(token)).toList();
        }

        @Override
        public List<PushTicket> send(List<PushMessage> messages) {
            sent.addAll(messages);
            return messages.stream().map(m -> errors.getOrDefault(m.to(), PushTicket.ok("ticket-" + m.to()))).toList();
        }
    }
}
