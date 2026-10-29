package com.compliancevault.vaultapi.service;

import com.compliancevault.vaultapi.repository.ProcessedEventRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.processing.consumer.enabled", havingValue = "true", matchIfMissing = true)
public class DocumentEventConsumer {

    private static final String EVENT_TYPE = "DOCUMENT_RECEIVED";

    private final ObjectMapper objectMapper;
    private final DocumentProcessingService processingService;
    private final DocumentRedactionService redactionService;
    private final ProcessedEventRepository processedEventRepository;

    public DocumentEventConsumer(
            ObjectMapper objectMapper,
            DocumentProcessingService processingService,
            DocumentRedactionService redactionService,
            ProcessedEventRepository processedEventRepository) {
        this.objectMapper = objectMapper;
        this.processingService = processingService;
        this.redactionService = redactionService;
        this.processedEventRepository = processedEventRepository;
    }

    @KafkaListener(topics = "${app.outbox.publisher.topic:document-events}", groupId = "${spring.kafka.consumer.group-id:vault-api-processing}")
    public void consume(ConsumerRecord<String, String> record) throws Exception {
        UUID eventId = UUID.fromString(record.key());
        JsonNode payload = objectMapper.readTree(record.value());

        UUID documentId = UUID.fromString(requiredText(payload, "documentId"));
        String objectKey = requiredText(payload, "objectKey");
        String contentType = requiredText(payload, "contentType");

        UUID claimToken = processingService.beginProcessing(
                eventId, documentId, EVENT_TYPE);

        if (claimToken == null) {
            if (processedEventRepository.isCompleted(eventId)) {
                return;
            }

            throw new IllegalStateException(
                    "Event is already being processed or its retry is not due: "
                            + eventId);
        }

        try {
            redactionService.redactAndStore(
                    eventId, documentId, objectKey, contentType);

            processingService.completeProcessing(
                    eventId, documentId, claimToken);
        } catch (Exception exception) {
            try {
                processingService.failProcessing(
                        eventId,
                        documentId,
                        claimToken,
                        safeErrorMessage(exception));
            } catch (Exception failureUpdateException) {
                // Preserve the original processing failure.
                exception.addSuppressed(failureUpdateException);
            }

            throw exception;
        }
    }

    private String requiredText(JsonNode payload, String field) {
        JsonNode value = payload.get(field);

        if (value == null || value.isNull() || value.asText().isBlank()) {
            throw new IllegalArgumentException(
                    "Event payload is missing required field: " + field);
        }

        return value.asText();
    }

    private String safeErrorMessage(Exception exception) {
        return exception.getClass().getSimpleName()
                + ": Document processing failed. See application logs.";
    }
}