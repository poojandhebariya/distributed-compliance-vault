package com.compliancevault.vaultapi.repository;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OutboxEventRepositoryTest {

    private JdbcTemplate jdbcTemplate;
    private OutboxEventRepository repository;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        repository = new OutboxEventRepository(jdbcTemplate);
    }

    @Test
    void recordFailureUsesExponentialRetryDelay() {
        UUID eventId = UUID.randomUUID();

        for (int attempts = 1; attempts <= 10; attempts++) {
            repository.recordFailure(eventId, attempts, "Kafka unavailable");

            long expectedSeconds = 1L << Math.min(attempts - 1, 8);

            verify(jdbcTemplate).update(
                    contains("next_attempt_at"),
                    eq("Kafka unavailable"),
                    eq(expectedSeconds),
                    eq(eventId));

            clearInvocations(jdbcTemplate);
        }
    }

    @Test
    void recordFailureHandlesNullErrorMessage() {
        UUID eventId = UUID.randomUUID();

        repository.recordFailure(eventId, 1, null);

        verify(jdbcTemplate).update(
                contains("next_attempt_at"),
                eq("Unknown publishing error"),
                eq(1L),
                eq(eventId));
    }

    @Test
    void recordFailureTruncatesLongErrorMessage() {
        UUID eventId = UUID.randomUUID();
        String longMessage = "x".repeat(5000);

        repository.recordFailure(eventId, 1, longMessage);

        verify(jdbcTemplate).update(
                contains("next_attempt_at"),
                argThat((String message) -> message.length() == 4000),
                eq(1L),
                eq(eventId));
    }

    @Test
    void markPublishedThrowsWhenNoUnpublishedEventIsUpdated() {
        UUID eventId = UUID.randomUUID();
        when(jdbcTemplate.update(anyString(), eq(eventId))).thenReturn(0);

        assertThrows(
                IllegalStateException.class,
                () -> repository.markPublished(eventId));
    }

    @Test
    void markPublishedSucceedsWhenEventIsUpdated() {
        UUID eventId = UUID.randomUUID();
        when(jdbcTemplate.update(anyString(), eq(eventId))).thenReturn(1);

        assertDoesNotThrow(() -> repository.markPublished(eventId));

        verify(jdbcTemplate).update(
                contains("published_at = NOW()"),
                eq(eventId));
    }
}
