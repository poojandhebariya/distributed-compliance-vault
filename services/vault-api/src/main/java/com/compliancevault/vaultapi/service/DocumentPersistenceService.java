
package com.compliancevault.vaultapi.service;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentPersistenceService {

    private final JdbcTemplate jdbcTemplate;

    public DocumentPersistenceService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public UUID recordUploadedDocument(
            String originalFilename,
            String contentType,
            String objectKey) {

        UUID documentId = UUID.randomUUID();

        String filename = originalFilename;
        if (filename == null || filename.isBlank()) {
            filename = "uploaded-document";
        }

        String type = contentType;
        if (type == null || type.isBlank()) {
            type = "application/octet-stream";
        }

        jdbcTemplate.update(
                """
                INSERT INTO documents (
                    id, original_filename, content_type, object_key
                )
                VALUES (?, ?, ?, ?)
                """,
                documentId,
                filename,
                type,
                objectKey
        );

        jdbcTemplate.update(
                """
                INSERT INTO outbox_events (
                    id, aggregate_type, aggregate_id, event_type, payload
                )
                VALUES (
                    ?, ?, ?, ?,
                    jsonb_build_object(
                        'documentId', ?,
                        'objectKey', ?,
                        'originalFilename', ?,
                        'contentType', ?
                    )
                )
                """,
                UUID.randomUUID(),
                "DOCUMENT",
                documentId,
                "DOCUMENT_RECEIVED",
                documentId,
                objectKey,
                filename,
                type
        );

        return documentId;
    }
}
