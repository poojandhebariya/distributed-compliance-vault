
package com.compliancevault.vaultapi.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxPublisherScheduler {

    private static final Logger log =
            LoggerFactory.getLogger(OutboxPublisherScheduler.class);

    private final OutboxPublisherService publisherService;
    private final boolean enabled;

    public OutboxPublisherScheduler(
            OutboxPublisherService publisherService,
            @Value("${app.outbox.publisher.enabled:true}") boolean enabled) {
        this.publisherService = publisherService;
        this.enabled = enabled;
    }

    @Scheduled(
            fixedDelayString = "${app.outbox.publisher.poll-interval-ms:5000}"
    )
    public void publishPendingEvents() {
        if (!enabled) {
            return;
        }

        try {
            publisherService.publishPendingEvents();
        } catch (Exception exception) {
            log.error("Unexpected error in outbox publisher cycle", exception);
        }
    }
}
