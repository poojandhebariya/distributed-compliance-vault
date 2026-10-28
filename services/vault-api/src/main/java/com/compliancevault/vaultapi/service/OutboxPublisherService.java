
package com.compliancevault.vaultapi.service;

import com.compliancevault.vaultapi.model.OutboxEvent;
import com.compliancevault.vaultapi.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class OutboxPublisherService {

    private static final Logger log =
            LoggerFactory.getLogger(OutboxPublisherService.class);

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;
    private final int batchSize;

    public OutboxPublisherService(
            OutboxEventRepository repository,
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${app.outbox.publisher.topic:document-events}") String topic,
            @Value("${app.outbox.publisher.batch-size:10}") int batchSize) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.batchSize = batchSize;
    }

    public void publishPendingEvents() {
        List<OutboxEvent> events =
                repository.claimEligibleEvents(batchSize);

        for (OutboxEvent event : events) {
            publishOne(event);
        }
    }

    private void publishOne(OutboxEvent event) {
        try {
            kafkaTemplate.send(
                    topic,
                    event.id().toString(),
                    event.payload()
            ).get(10, TimeUnit.SECONDS);

            repository.markPublished(event.id());

            log.info(
                    "Published outbox event id={} type={} aggregateId={}",
                    event.id(),
                    event.eventType(),
                    event.aggregateId()
            );
        } catch (Exception exception) {
            String message = exception.getMessage();

            log.error(
                    "Failed to publish outbox event id={} attempt={}",
                    event.id(),
                    event.attempts(),
                    exception
            );

            try {
                repository.recordFailure(
                        event.id(),
                        event.attempts(),
                        message
                );
            } catch (Exception persistenceException) {
                log.error(
                        "Could not record publishing failure for event id={}",
                        event.id(),
                        persistenceException
                );
            }
        }
    }
}
