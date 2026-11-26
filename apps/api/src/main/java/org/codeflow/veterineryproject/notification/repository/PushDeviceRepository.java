package org.codeflow.veterineryproject.notification.repository;

import org.codeflow.veterineryproject.notification.dto.PushDeviceResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** vet.push_devices: kullanıcıların Expo push token'ları (organizasyon başına token tekil). */
@Repository
public class PushDeviceRepository {

    private final JdbcTemplate jdbc;

    public PushDeviceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Aynı token tekrar gelirse son görülme güncellenir; başka kullanıcıdan gelirse token ona taşınır. */
    public PushDeviceResponse upsert(UUID organizationId, UUID userId, String token, String platform) {
        return jdbc.queryForObject("""
                insert into vet.push_devices (organization_id, user_id, expo_push_token, platform)
                values (?, ?, ?, ?)
                on conflict (organization_id, expo_push_token)
                do update set user_id = excluded.user_id, platform = excluded.platform, last_seen_at = now()
                returning id, expo_push_token, platform, last_seen_at
                """, (rs, i) -> new PushDeviceResponse(rs.getObject("id", UUID.class), rs.getString("expo_push_token"),
                rs.getString("platform"), rs.getObject("last_seen_at", OffsetDateTime.class)),
                organizationId, userId, token, platform);
    }

    public int deleteOwn(UUID userId, String token) {
        return jdbc.update("delete from vet.push_devices where user_id = ? and expo_push_token = ?", userId, token);
    }

    public List<String> findTokens(UUID userId) {
        return jdbc.queryForList("select expo_push_token from vet.push_devices where user_id = ? order by created_at",
                String.class, userId);
    }

    public boolean existsForUser(UUID userId) {
        Boolean exists = jdbc.queryForObject("select exists (select 1 from vet.push_devices where user_id = ?)",
                Boolean.class, userId);
        return Boolean.TRUE.equals(exists);
    }

    public int deleteTokens(List<String> tokens) {
        if (tokens.isEmpty()) {
            return 0;
        }
        return jdbc.update("delete from vet.push_devices where expo_push_token = any (?)",
                (Object) tokens.toArray(String[]::new));
    }
}
