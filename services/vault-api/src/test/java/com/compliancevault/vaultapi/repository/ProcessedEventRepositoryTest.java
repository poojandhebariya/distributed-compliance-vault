package com.compliancevault.vaultapi.repository;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProcessedEventRepositoryTest {

    private JdbcTemplate jdbcTemplate;
    private ProcessedEventRepository repository;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        repository = new ProcessedEventRepository(jdbcTemplate);
    }

    @Test
    void markFailedSucceedsWhenCurrentClaimIsUpdated() {
        UUID eventId = UUID.randomUUID();
        UUID claimToken = UUID.randomUUID();

        when(jdbcTemplate.update(
                anyString(),
                eq("Storage temporarily unavailable"),
                eq(eventId),
                eq(claimToken)
        )).thenReturn(1);

        assertDoesNotThrow(() -> repository.markFailed(
                eventId, claimToken, "Storage temporarily unavailable"));

        verify(jdbcTemplate).update(
                anyString(),
                eq("Storage temporarily unavailable"),
                eq(eventId),
                eq(claimToken)
        );
    }

    @Test
    void markFailedThrowsWhenCurrentClaimIsNotUpdated() {
        UUID eventId = UUID.randomUUID();
        UUID claimToken = UUID.randomUUID();

        when(jdbcTemplate.update(
                anyString(),
                eq("Storage temporarily unavailable"),
                eq(eventId),
                eq(claimToken)
        )).thenReturn(0);

        assertThrows(
                IllegalStateException.class,
                () -> repository.markFailed(
                        eventId, claimToken, "Storage temporarily unavailable"));
    }

    @Test
    void markCompletedThrowsWhenCurrentClaimIsNotUpdated() {
        UUID eventId = UUID.randomUUID();
        UUID claimToken = UUID.randomUUID();

        when(jdbcTemplate.update(
                anyString(),
                eq(eventId),
                eq(claimToken)
        )).thenReturn(0);

        assertThrows(
                IllegalStateException.class,
                () -> repository.markCompleted(eventId, claimToken));
    }
}