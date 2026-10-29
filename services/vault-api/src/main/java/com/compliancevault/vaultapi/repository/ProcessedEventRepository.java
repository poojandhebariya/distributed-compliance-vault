
package com.compliancevault.vaultapi.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProcessedEventRepository {

    private final JdbcTemplate jdbcTemplate;

    public ProcessedEventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<UUID> claimEvent(
            UUID eventId,
            UUID documentId,
            String eventType) {

        UUID claimToken = UUID.randomUUID();

        var tokens = jdbcTemplate.query(
                """
                        INSERT INTO processed_events (
                            event_id,
                            document_id,
                            event_type,
                            claim_token,
                            status,
                            attempts,
                            started_at,
                            next_attempt_at
                        )
                        VALUES (?, ?, ?, ?, 'PROCESSING', 1, NOW(), NOW())

                        ON CONFLICT (event_id) DO UPDATE
                        SET status = 'PROCESSING',
                            claim_token = EXCLUDED.claim_token,
                            attempts = processed_events.attempts + 1,
                            started_at = NOW(),
                            next_attempt_at = NOW(),
                            last_error = NULL

                        WHERE processed_events.document_id = EXCLUDED.document_id
                          AND processed_events.event_type = EXCLUDED.event_type
                          AND processed_events.status <> 'COMPLETED'
                          AND (
                              (
                                  processed_events.status = 'FAILED'
                                  AND processed_events.next_attempt_at <= NOW()
                              )
                              OR
                              (
                                  processed_events.status = 'PROCESSING'
                                  AND processed_events.started_at
                                      <= NOW() - INTERVAL '5 minutes'
                              )
                          )
                        RETURNING claim_token
                        """,
                (rs, rowNum) -> rs.getObject("claim_token", UUID.class),
                eventId,
                documentId,
                eventType,
                claimToken);

        return tokens.stream().findFirst();
    }

    public void markCompleted(UUID eventId, UUID claimToken) {
        int updated = jdbcTemplate.update(
                """
                        UPDATE processed_events
                        SET status = 'COMPLETED',
                            processed_at = NOW(),
                            last_error = NULL
                        WHERE event_id = ?
                          AND claim_token = ?
                          AND status = 'PROCESSING'
                        """,
                eventId,
                claimToken);

        if (updated != 1) {
            throw new IllegalStateException(
                    "Could not complete event for current claim: " + eventId);
        }
    }

    public void markFailed(
            UUID eventId,
            UUID claimToken,
            String errorMessage) {

        int updated = jdbcTemplate.update(
                """
                        UPDATE processed_events
                        SET status = 'FAILED',
                            last_error = ?,
                            next_attempt_at = NOW() + INTERVAL '30 seconds'
                        WHERE event_id = ?
                          AND claim_token = ?
                          AND status = 'PROCESSING'
                        """,
                errorMessage,
                eventId,
                claimToken);

        if (updated != 1) {
            throw new IllegalStateException(
                    "Could not mark event failed for current claim: " + eventId);
        }
    }

    public boolean isCompleted(UUID eventId) {
        Boolean completed = jdbcTemplate.queryForObject(
                """
                        SELECT EXISTS (
                            SELECT 1
                            FROM processed_events
                            WHERE event_id = ?
                              AND status = 'COMPLETED'
                        )
                        """,
                Boolean.class,
                eventId);

        return Boolean.TRUE.equals(completed);
    }
}
