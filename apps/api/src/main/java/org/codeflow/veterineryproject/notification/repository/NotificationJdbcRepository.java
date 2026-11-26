package org.codeflow.veterineryproject.notification.repository;

import org.codeflow.veterineryproject.notification.dto.DeliveryResponse;
import org.codeflow.veterineryproject.notification.dto.DeliverySummaryRow;
import org.codeflow.veterineryproject.notification.dto.NotificationResponse;
import org.codeflow.veterineryproject.notification.model.DeliveryStatus;
import org.codeflow.veterineryproject.notification.model.NotificationChannelType;
import org.codeflow.veterineryproject.notification.model.NotificationRecipient;
import org.codeflow.veterineryproject.notification.model.NotificationType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** vet.notification_outbox: gönderim kaydı (her kanal denemesi) ve IN_APP satırlarından oluşan gelen kutusu. */
@Repository
public class NotificationJdbcRepository {

    private static final int MAX_ERROR_LENGTH = 1000;

    /** Gönderim kaydı filtresi; from dahil, to hariç (zaman aralığı servis tarafında gün sınırına çevrilir). */
    public record DeliveryFilter(String channel, String status, String type, OffsetDateTime from, OffsetDateTime to) {
    }

    private static final String INBOX_COLUMNS = """
            id, notification_type, subject, message_body, animal_id, appointment_id, read_at, created_at
            """;

    private static final RowMapper<NotificationResponse> INBOX_MAPPER = (rs, i) -> new NotificationResponse(
            rs.getObject("id", UUID.class), rs.getString("notification_type"), rs.getString("subject"),
            rs.getString("message_body"), rs.getObject("animal_id", UUID.class), rs.getObject("appointment_id", UUID.class),
            rs.getObject("read_at", OffsetDateTime.class), rs.getObject("created_at", OffsetDateTime.class));

    private static final RowMapper<DeliveryResponse> DELIVERY_MAPPER = (rs, i) -> new DeliveryResponse(
            rs.getObject("id", UUID.class), rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("sent_at", OffsetDateTime.class), rs.getString("notification_type"), rs.getString("channel"),
            rs.getString("status"), rs.getObject("user_id", UUID.class), rs.getObject("client_id", UUID.class),
            rs.getString("recipient_name"), rs.getString("recipient"), rs.getString("subject"),
            rs.getInt("attempt_count"), rs.getString("last_error"));

    private final JdbcTemplate jdbc;

    public NotificationJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---- gönderim kaydı yazma -------------------------------------------------------------------------------------

    public UUID insert(UUID organizationId, NotificationType type, NotificationChannelType channel,
                       NotificationRecipient recipient, String title, String body, UUID animalId, UUID appointmentId,
                       DeliveryStatus status, String error) {
        String address = channel == NotificationChannelType.EMAIL ? recipient.email().trim() : null;
        return jdbc.queryForObject("""
                        insert into vet.notification_outbox (organization_id, notification_type, channel, user_id, client_id,
                            recipient, recipient_name, subject, message_body, animal_id, appointment_id, status, last_error)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        returning id
                        """, UUID.class, organizationId, type.name(), channel.name(),
                channel == NotificationChannelType.EMAIL ? null : recipient.userId(), recipient.clientId(),
                address, recipient.displayName(), title, body, animalId, appointmentId, status.name(), error);
    }

    public void markSent(UUID id, String providerMessageId) {
        jdbc.update("""
                update vet.notification_outbox
                set status = 'SENT', sent_at = now(), attempt_count = attempt_count + 1,
                    provider_message_id = ?, last_error = null
                where id = ?
                """, providerMessageId, id);
    }

    public void markFailed(UUID id, String error) {
        String message = error == null ? "Bilinmeyen hata" : error;
        jdbc.update("""
                update vet.notification_outbox
                set status = 'FAILED', attempt_count = attempt_count + 1, last_error = ?
                where id = ?
                """, message.length() > MAX_ERROR_LENGTH ? message.substring(0, MAX_ERROR_LENGTH) : message, id);
    }

    // ---- gelen kutusu (yalnız IN_APP ve teslim edilmiş satırlar) -----------------------------------------------------

    private static final String INBOX_WHERE = " where user_id = ? and channel = 'IN_APP' and status = 'SENT'";

    public List<NotificationResponse> findInbox(UUID userId, boolean unreadOnly, int limit, long offset) {
        return jdbc.query("select " + INBOX_COLUMNS + " from vet.notification_outbox" + INBOX_WHERE
                        + (unreadOnly ? " and read_at is null" : "") + " order by created_at desc, id desc limit ? offset ?",
                INBOX_MAPPER, userId, limit, offset);
    }

    public long countInbox(UUID userId, boolean unreadOnly) {
        Long n = jdbc.queryForObject("select count(*) from vet.notification_outbox" + INBOX_WHERE
                + (unreadOnly ? " and read_at is null" : ""), Long.class, userId);
        return n == null ? 0 : n;
    }

    /** Sahiplik sorgunun içindedir: başkasının bildirimi boş döner (→ 404). */
    public Optional<NotificationResponse> markRead(UUID userId, UUID id) {
        return jdbc.query("update vet.notification_outbox set read_at = coalesce(read_at, now())" + INBOX_WHERE
                + " and id = ? returning " + INBOX_COLUMNS, INBOX_MAPPER, userId, id).stream().findFirst();
    }

    public int markAllRead(UUID userId) {
        return jdbc.update("update vet.notification_outbox set read_at = now()" + INBOX_WHERE + " and read_at is null", userId);
    }

    // ---- yönetici: gönderim kayıtları --------------------------------------------------------------------------------

    public List<DeliveryResponse> findDeliveries(DeliveryFilter filter, int limit, long offset) {
        List<Object> args = new ArrayList<>();
        String where = deliveryWhere(filter, args);
        args.add(limit);
        args.add(offset);
        return jdbc.query("""
                select id, created_at, sent_at, notification_type, channel, status, user_id, client_id, recipient_name,
                       recipient, subject, attempt_count, last_error
                from vet.notification_outbox
                """ + where + " order by created_at desc, id desc limit ? offset ?", DELIVERY_MAPPER, args.toArray());
    }

    public long countDeliveries(DeliveryFilter filter) {
        List<Object> args = new ArrayList<>();
        Long n = jdbc.queryForObject("select count(*) from vet.notification_outbox" + deliveryWhere(filter, args),
                Long.class, args.toArray());
        return n == null ? 0 : n;
    }

    public List<DeliverySummaryRow> summarizeDeliveries(DeliveryFilter filter) {
        List<Object> args = new ArrayList<>();
        return jdbc.query("select channel, status, count(*) as n from vet.notification_outbox" + deliveryWhere(filter, args)
                        + " group by channel, status order by channel, status",
                (rs, i) -> new DeliverySummaryRow(rs.getString("channel"), rs.getString("status"), rs.getLong("n")),
                args.toArray());
    }

    /** Organizasyon sınırını RLS çizer; burada yalnız kullanıcı filtreleri eklenir. */
    private static String deliveryWhere(DeliveryFilter f, List<Object> args) {
        StringBuilder sql = new StringBuilder(" where notification_type is not null");
        if (f.channel() != null) {
            sql.append(" and channel = ?");
            args.add(f.channel());
        }
        if (f.status() != null) {
            sql.append(" and status = ?");
            args.add(f.status());
        }
        if (f.type() != null) {
            sql.append(" and notification_type = ?");
            args.add(f.type());
        }
        if (f.from() != null) {
            sql.append(" and created_at >= ?");
            args.add(f.from());
        }
        if (f.to() != null) {
            sql.append(" and created_at < ?");
            args.add(f.to());
        }
        return sql.toString();
    }
}
