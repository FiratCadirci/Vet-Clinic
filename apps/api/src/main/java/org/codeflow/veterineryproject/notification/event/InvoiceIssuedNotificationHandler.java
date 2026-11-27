package org.codeflow.veterineryproject.notification.event;

import org.codeflow.veterineryproject.common.event.DomainEvents.InvoiceIssuedEvent;
import org.codeflow.veterineryproject.notification.model.NotificationMessage;
import org.codeflow.veterineryproject.notification.model.NotificationType;
import org.codeflow.veterineryproject.notification.repository.NotificationRecipientRepository;
import org.codeflow.veterineryproject.notification.repository.NotificationRecipientRepository.InvoiceNotice;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Satış faturası kesildi → faturanın müşterisine (mobil hesabı ve/veya e-postası), numara ve tutarla (FG-I1). */
@Component
public class InvoiceIssuedNotificationHandler implements NotificationEventHandler<InvoiceIssuedEvent> {

    private static final Locale TR = Locale.forLanguageTag("tr-TR");

    private final NotificationRecipientRepository repository;

    public InvoiceIssuedNotificationHandler(NotificationRecipientRepository repository) {
        this.repository = repository;
    }

    @Override
    public Class<InvoiceIssuedEvent> eventType() {
        return InvoiceIssuedEvent.class;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<NotificationMessage> compose(InvoiceIssuedEvent event) {
        return repository.findIssuedInvoice(event.invoiceId()).flatMap(this::message);
    }

    private Optional<NotificationMessage> message(InvoiceNotice invoice) {
        if (invoice.owner().userId() == null && (invoice.owner().email() == null || invoice.owner().email().isBlank())) {
            return Optional.empty();
        }
        String body = "%s şubesinde %s numaralı faturanız kesildi. Tutar: %s.".formatted(
                NotificationTexts.orDefault(invoice.clinicName(), "Klinik"), invoice.invoiceNo(),
                money(invoice.grandTotal(), invoice.currency()));
        return Optional.of(NotificationMessage.of(NotificationType.INVOICE_ISSUED, "Faturanız kesildi", body,
                invoice.animalId(), null, List.of(invoice.owner().toRecipient())));
    }

    private static String money(BigDecimal value, String currency) {
        String amount = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(TR)).format(value);
        return amount + ("TRY".equals(currency) || currency == null ? " TL" : " " + currency);
    }
}
