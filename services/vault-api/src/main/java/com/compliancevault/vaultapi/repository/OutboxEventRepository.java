
package com.compliancevault.vaultapi.repository;

import com.compliancevault.vaultapi.model.OutboxEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
public class OutboxEventRepository {

    private final JdbcTemplate jdbcTemplate;

    public OutboxEventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public List<OutboxEvent> claimEligibleEvents(int batchSize) {
        return jdbcTemplate.query(
                """
                WITH candidates AS (
                    SELECT id
                    FROM outbox_events
                    WHERE published_at IS NULL
                      AND next_attempt_at <= NOW()
                    ORDER BY created_at
                    LIMIT ?
                    FOR UPDATE SKIP LOCKED
                )
                UPDATE outbox_events AS e
                SET attempts = e.attempts + 1,
                    next_attempt_at = NOW() + INTERVAL '2 minutes'
                FROM candidates AS c
                WHERE e.id = c.id
                RETURNING
                    e.id,
                    e.aggregate_type,
                    e.aggregate_id,
                    e.event_type,
                    e.payload::text AS payload,
                    e.attempts
                """,
                (rs, rowNum) -> new OutboxEvent(
                        rs.getObject("id", UUID.class),
                        rs.getString("aggregate_type"),
                        rs.getObject("aggregate_id", UUID.class),
                        rs.getString("event_type"),
                        rs.getString("payload"),
                        rs.getInt("attempts")
                ),
                batchSize
        );
    }

    @Transactional
    public void markPublished(UUID eventId) {
        int updated = jdbcTemplate.update(
                """
                UPDATE outbox_events
                SET published_at = NOW(),
                    last_error = NULL
                WHERE id = ?
                  AND published_at IS NULL
                """,
                eventId
        );

        if (updated != 1) {
            throw new IllegalStateException(
                    "Could not mark outbox event as published: " + eventId
            );
        }
    }

    @Transactional
    public void recordFailure(
            UUID eventId,
            int attempts,
            String errorMessage) {

        int exponent = Math.max(0, Math.min(attempts - 1, 8));
        long retryDelaySeconds = 1L << exponent;

        String safeError = errorMessage == null
                ? "Unknown publishing error"
                : errorMessage;

        if (safeError.length() > 4000) {
            safeError = safeError.substring(0, 4000);
        }

        jdbcTemplate.update(
                """
                UPDATE outbox_events
                SET last_error = ?,
                    next_attempt_at =
                        NOW() + (? * INTERVAL '1 second')
                WHERE id = ?
                  AND published_at IS NULL
                """,
                safeError,
                retryDelaySeconds,
                eventId
        );
    }
}
