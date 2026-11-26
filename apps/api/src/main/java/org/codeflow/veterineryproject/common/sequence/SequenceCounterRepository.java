package org.codeflow.veterineryproject.common.sequence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class SequenceCounterRepository {

    private final JdbcTemplate jdbc;

    public SequenceCounterRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Sayaç satırı transaction sonuna kadar kilitlenir; eşzamanlı kayıtlar sıraya girer. */
    public long next(UUID organizationId, String key) {
        Long value = jdbc.queryForObject("""
                insert into vet.sequence_counters (organization_id, counter_key, last_value) values (?, ?, 1)
                on conflict (organization_id, counter_key)
                do update set last_value = vet.sequence_counters.last_value + 1, updated_at = now()
                returning last_value
                """, Long.class, organizationId, key);
        return value == null ? 1 : value;
    }
}
