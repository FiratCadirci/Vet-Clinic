package org.codeflow.veterineryproject.notification.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** vet.notification_preferences: kullanıcı × olay türü × kanal açık/kapalı. Satır yoksa kanal açıktır. */
@Repository
public class NotificationPreferenceRepository {

    public record StoredPreference(String type, String channel, boolean enabled) {
    }

    private final JdbcTemplate jdbc;

    public NotificationPreferenceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<StoredPreference> findByUser(UUID userId) {
        return jdbc.query("""
                select notification_type, channel, is_enabled from vet.notification_preferences
                where user_id = ? order by notification_type, channel
                """, (rs, i) -> new StoredPreference(rs.getString(1), rs.getString(2), rs.getBoolean(3)), userId);
    }

    public Optional<Boolean> findEnabled(UUID userId, String type, String channel) {
        return jdbc.query("""
                select is_enabled from vet.notification_preferences
                where user_id = ? and notification_type = ? and channel = ?
                """, (rs, i) -> rs.getBoolean(1), userId, type, channel).stream().findFirst();
    }

    public void upsert(UUID organizationId, UUID userId, String type, String channel, boolean enabled) {
        jdbc.update("""
                insert into vet.notification_preferences (organization_id, user_id, notification_type, channel, is_enabled)
                values (?, ?, ?, ?, ?)
                on conflict (organization_id, user_id, notification_type, channel) where user_id is not null
                do update set is_enabled = excluded.is_enabled, updated_at = now()
                """, organizationId, userId, type, channel, enabled);
    }
}
