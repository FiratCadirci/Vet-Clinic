package org.codeflow.veterineryproject.notification.service;

import jakarta.persistence.EntityNotFoundException;
import org.codeflow.veterineryproject.common.pagination.PageResponse;
import org.codeflow.veterineryproject.common.pagination.PaginationUtils;
import org.codeflow.veterineryproject.common.tenant.TenantContext;
import org.codeflow.veterineryproject.notification.dto.MarkAllReadResponse;
import org.codeflow.veterineryproject.notification.dto.NotificationResponse;
import org.codeflow.veterineryproject.notification.dto.UnreadCountResponse;
import org.codeflow.veterineryproject.notification.repository.NotificationJdbcRepository;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Oturumdaki kullanıcının (personel ya da hayvan sahibi) uygulama içi bildirimleri. */
@Service
public class NotificationInboxService {

    private final NotificationJdbcRepository repository;

    public NotificationInboxService(NotificationJdbcRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(boolean unreadOnly, Integer page, Integer size) {
        UUID userId = TenantContext.require().userId();
        Pageable pageable = PaginationUtils.createPageable(page, size);
        return PageResponse.from(new PageImpl<>(
                repository.findInbox(userId, unreadOnly, pageable.getPageSize(), pageable.getOffset()),
                pageable, repository.countInbox(userId, unreadOnly)));
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse unreadCount() {
        return new UnreadCountResponse(repository.countInbox(TenantContext.require().userId(), true));
    }

    @Transactional
    public NotificationResponse markRead(UUID id) {
        return repository.markRead(TenantContext.require().userId(), id)
                .orElseThrow(() -> new EntityNotFoundException("Bildirim bulunamadı."));
    }

    @Transactional
    public MarkAllReadResponse markAllRead() {
        return new MarkAllReadResponse(repository.markAllRead(TenantContext.require().userId()));
    }
}
