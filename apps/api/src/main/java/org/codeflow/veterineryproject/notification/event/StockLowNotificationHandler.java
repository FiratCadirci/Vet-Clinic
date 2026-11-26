package org.codeflow.veterineryproject.notification.event;

import org.codeflow.veterineryproject.auth.security.Permission;
import org.codeflow.veterineryproject.common.event.DomainEvents.StockLowEvent;
import org.codeflow.veterineryproject.notification.model.NotificationMessage;
import org.codeflow.veterineryproject.notification.model.NotificationRecipient;
import org.codeflow.veterineryproject.notification.model.NotificationType;
import org.codeflow.veterineryproject.notification.repository.NotificationRecipientRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** Kritik stok → o şubede stok yazma yetkisi (inventory.write) olan personele uygulama içi uyarı. */
@Component
public class StockLowNotificationHandler implements NotificationEventHandler<StockLowEvent> {

    private final NotificationRecipientRepository repository;

    public StockLowNotificationHandler(NotificationRecipientRepository repository) {
        this.repository = repository;
    }

    @Override
    public Class<StockLowEvent> eventType() {
        return StockLowEvent.class;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<NotificationMessage> compose(StockLowEvent event) {
        List<NotificationRecipient> staff = repository.findClinicStaff(event.clinicId(), Permission.INVENTORY_WRITE);
        if (staff.isEmpty()) {
            return Optional.empty();
        }
        String body = "%s: şubede %s kaldı (minimum %s).".formatted(NotificationTexts.orDefault(event.productName(), "Ürün"),
                NotificationTexts.quantity(event.quantity()), NotificationTexts.quantity(event.minimum()));
        return Optional.of(NotificationMessage.of(NotificationType.STOCK_LOW, "Kritik stok uyarısı", body, null, null, staff));
    }
}
