package org.codeflow.veterineryproject.notification.event;

import org.codeflow.veterineryproject.common.tenant.TenantContext;
import org.codeflow.veterineryproject.notification.model.NotificationRecipient;
import org.codeflow.veterineryproject.notification.repository.NotificationRecipientRepository;
import org.codeflow.veterineryproject.notification.repository.NotificationRecipientRepository.OwnerContact;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Hayvan sahibine giden bildirimlerin ortak alıcı kuralı: güncel sahipler, işlemi yapan kişi hariç. */
@Component
class OwnerRecipients {

    private final NotificationRecipientRepository repository;

    OwnerRecipients(NotificationRecipientRepository repository) {
        this.repository = repository;
    }

    record Owners(String animalName, List<NotificationRecipient> recipients) {
        boolean isEmpty() {
            return recipients.isEmpty();
        }
    }

    Owners of(UUID animalId) {
        List<OwnerContact> owners = repository.findAnimalOwners(animalId);
        UUID actor = TenantContext.get().map(TenantContext.ContextValues::userId).orElse(null);
        String animalName = owners.isEmpty() ? null : owners.get(0).animalName();
        // Sahip işlemi kendisi yaptıysa (ör. portaldan iptal) kendisine bildirim gitmez.
        List<NotificationRecipient> recipients = owners.stream()
                .filter(o -> actor == null || !Objects.equals(o.userId(), actor))
                .map(OwnerContact::toRecipient)
                .toList();
        return new Owners(NotificationTexts.orDefault(animalName, "Hastanız"), recipients);
    }
}
