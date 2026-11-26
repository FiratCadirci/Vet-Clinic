package org.codeflow.veterineryproject.notification.service;

import org.codeflow.veterineryproject.common.pagination.PageResponse;
import org.codeflow.veterineryproject.common.pagination.PaginationUtils;
import org.codeflow.veterineryproject.common.time.ApiDateTime;
import org.codeflow.veterineryproject.notification.dto.DeliveryResponse;
import org.codeflow.veterineryproject.notification.dto.DeliverySummaryRow;
import org.codeflow.veterineryproject.notification.model.DeliveryStatus;
import org.codeflow.veterineryproject.notification.model.NotificationChannelType;
import org.codeflow.veterineryproject.notification.model.NotificationType;
import org.codeflow.veterineryproject.notification.repository.NotificationJdbcRepository;
import org.codeflow.veterineryproject.notification.repository.NotificationJdbcRepository.DeliveryFilter;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Yönetici için gönderim kayıtları (FG-I3). Organizasyon sınırını RLS çizer: yalnız kendi organizasyonu. */
@Service
public class DeliveryLogService {

    /** Uç noktadaki filtre parametreleri; boş değerler filtre uygulanmaz demektir. */
    public record Query(String channel, String status, String type, LocalDate from, LocalDate to) {
    }

    private final NotificationJdbcRepository repository;

    public DeliveryLogService(NotificationJdbcRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PageResponse<DeliveryResponse> list(Query query, Integer page, Integer size) {
        DeliveryFilter filter = toFilter(query);
        Pageable pageable = PaginationUtils.createPageable(page, size);
        return PageResponse.from(new PageImpl<>(
                repository.findDeliveries(filter, pageable.getPageSize(), pageable.getOffset()),
                pageable, repository.countDeliveries(filter)));
    }

    @Transactional(readOnly = true)
    public List<DeliverySummaryRow> summary(Query query) {
        return repository.summarizeDeliveries(toFilter(query));
    }

    private static DeliveryFilter toFilter(Query q) {
        if (q.from() != null && q.to() != null && q.from().isAfter(q.to())) {
            throw new IllegalArgumentException("Başlangıç tarihi bitiş tarihinden sonra olamaz.");
        }
        return new DeliveryFilter(
                parse(q.channel(), NotificationChannelType.values(), "kanal"),
                parse(q.status(), DeliveryStatus.values(), "durum"),
                parse(q.type(), NotificationType.values(), "bildirim türü"),
                q.from() == null ? null : q.from().atStartOfDay(ApiDateTime.ZONE).toOffsetDateTime(),
                q.to() == null ? null : q.to().plusDays(1).atStartOfDay(ApiDateTime.ZONE).toOffsetDateTime());
    }

    private static <E extends Enum<E>> String parse(String value, E[] values, String label) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values).map(Enum::name).filter(normalized::equals).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Geçersiz " + label + ": " + value));
    }
}
