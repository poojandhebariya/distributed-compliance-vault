
package com.compliancevault.vaultapi.service;

import java.util.UUID;

import com.compliancevault.vaultapi.repository.ProcessedEventRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentProcessingService {

    private final JdbcTemplate jdbcTemplate;
    private final ProcessedEventRepository processedEventRepository;

    public DocumentProcessingService(
            JdbcTemplate jdbcTemplate,
            ProcessedEventRepository processedEventRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.processedEventRepository = processedEventRepository;
    }

    @Transactional
    public UUID beginProcessing(
            UUID eventId,
            UUID documentId,
            String eventType) {

        var claim = processedEventRepository.claimEvent(
                eventId,
                documentId,
                eventType
        );

        if (claim.isEmpty()) {
            return null;
        }

        UUID claimToken = claim.get();

        int updated = jdbcTemplate.update(
                """
                UPDATE documents
                SET status = 'PROCESSING',
                    updated_at = NOW()
                WHERE id = ?
                  AND status IN ('RECEIVED', 'PROCESSING', 'FAILED')
                """,
                documentId
        );

        if (updated != 1) {
            throw new IllegalStateException(
                    "Document is missing or cannot be processed: "
                            + documentId
            );
        }

        return claimToken;
    }

    @Transactional
    public void completeProcessing(
            UUID eventId,
            UUID documentId,
            UUID claimToken) {

        int updated = jdbcTemplate.update(
                """
                UPDATE documents
                SET status = 'COMPLETED',
                    updated_at = NOW()
                WHERE id = ?
                  AND status = 'PROCESSING'
                  AND EXISTS (
                      SELECT 1
                      FROM processed_events
                      WHERE event_id = ?
                        AND document_id = documents.id
                        AND claim_token = ?
                        AND status = 'PROCESSING'
                  )
                """,
                documentId,
                eventId,
                claimToken
        );

        if (updated != 1) {
            throw new IllegalStateException(
                    "Could not complete document for current claim: "
                            + documentId
            );
        }

        processedEventRepository.markCompleted(eventId, claimToken);
    }

    @Transactional
    public void failProcessing(
            UUID eventId,
            UUID documentId,
            UUID claimToken,
            String errorMessage) {

        processedEventRepository.markFailed(
                eventId,
                claimToken,
                errorMessage
        );

        int updated = jdbcTemplate.update(
                """
                UPDATE documents
                SET status = 'FAILED',
                    updated_at = NOW()
                WHERE id = ?
                  AND status = 'PROCESSING'
                  AND EXISTS (
                      SELECT 1
                      FROM processed_events
                      WHERE event_id = ?
                        AND document_id = documents.id
                        AND claim_token = ?
                        AND status = 'FAILED'
                  )
                """,
                documentId,
                eventId,
                claimToken
        );

        if (updated != 1) {
            throw new IllegalStateException(
                    "Could not mark document failed for current claim: "
                            + documentId
            );
        }
    }
}
