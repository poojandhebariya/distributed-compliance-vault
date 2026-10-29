package com.compliancevault.vaultapi.service;

import java.util.Optional;
import java.util.UUID;

import com.compliancevault.vaultapi.repository.ProcessedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DocumentProcessingServiceTest {

    private JdbcTemplate jdbcTemplate;
    private ProcessedEventRepository repository;
    private DocumentProcessingService service;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        repository = mock(ProcessedEventRepository.class);
        service = new DocumentProcessingService(jdbcTemplate, repository);
    }

    @Test
    void beginProcessingReturnsNullWhenEventCannotBeClaimed() {
        UUID eventId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();

        when(repository.claimEvent(eventId, documentId, "DOCUMENT_UPLOADED"))
                .thenReturn(Optional.empty());

        UUID result = service.beginProcessing(
                eventId, documentId, "DOCUMENT_UPLOADED");

        assertNull(result);
        verify(jdbcTemplate, never()).update(anyString(), any(), any());
    }

    @Test
    void beginProcessingReturnsClaimTokenWhenDocumentIsUpdated() {
        UUID eventId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID claimToken = UUID.randomUUID();

        when(repository.claimEvent(eventId, documentId, "DOCUMENT_UPLOADED"))
                .thenReturn(Optional.of(claimToken));
        when(jdbcTemplate.update(anyString(), eq(documentId))).thenReturn(1);

        UUID result = service.beginProcessing(
                eventId, documentId, "DOCUMENT_UPLOADED");

        assertEquals(claimToken, result);
    }

    @Test
    void beginProcessingThrowsWhenDocumentCannotBeUpdated() {
        UUID eventId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID claimToken = UUID.randomUUID();

        when(repository.claimEvent(eventId, documentId, "DOCUMENT_UPLOADED"))
                .thenReturn(Optional.of(claimToken));
        when(jdbcTemplate.update(anyString(), eq(documentId))).thenReturn(0);

        assertThrows(
                IllegalStateException.class,
                () -> service.beginProcessing(
                        eventId, documentId, "DOCUMENT_UPLOADED"));
    }

    @Test
    void completeProcessingMarksEventCompletedAfterDocumentUpdate() {
        UUID eventId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID claimToken = UUID.randomUUID();

        when(jdbcTemplate.update(anyString(), eq(documentId), eq(eventId), eq(claimToken)))
                .thenReturn(1);

        service.completeProcessing(eventId, documentId, claimToken);

        verify(repository).markCompleted(eventId, claimToken);
    }

    @Test
    void completeProcessingRejectsStaleClaim() {
        UUID eventId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID claimToken = UUID.randomUUID();

        when(jdbcTemplate.update(anyString(), eq(documentId), eq(eventId), eq(claimToken)))
                .thenReturn(0);

        assertThrows(
                IllegalStateException.class,
                () -> service.completeProcessing(eventId, documentId, claimToken));

        verify(repository, never()).markCompleted(any(), any());
    }

    @Test
    void failProcessingMarksEventAndDocumentFailed() {
        UUID eventId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID claimToken = UUID.randomUUID();
        String errorMessage = "Storage temporarily unavailable";

        when(jdbcTemplate.update(
                anyString(),
                eq(documentId),
                eq(eventId),
                eq(claimToken)
        )).thenReturn(1);

        service.failProcessing(
                eventId, documentId, claimToken, errorMessage);

        verify(repository).markFailed(eventId, claimToken, errorMessage);
        verify(jdbcTemplate).update(
                anyString(),
                eq(documentId),
                eq(eventId),
                eq(claimToken)
        );
    }

    @Test
    void failProcessingThrowsWhenDocumentUpdateAffectsNoRows() {
        UUID eventId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID claimToken = UUID.randomUUID();
        String errorMessage = "Storage temporarily unavailable";

        when(jdbcTemplate.update(
                anyString(),
                eq(documentId),
                eq(eventId),
                eq(claimToken)
        )).thenReturn(0);

        assertThrows(
                IllegalStateException.class,
                () -> service.failProcessing(
                        eventId, documentId, claimToken, errorMessage)
        );

        verify(repository).markFailed(eventId, claimToken, errorMessage);
    }}