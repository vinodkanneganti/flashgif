package com.flashgif.search.sync;

import com.fasterxml.jackson.databind.JsonNode;
import com.flashgif.infra.outbox.OutboxEvent;
import com.flashgif.infra.outbox.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Drains the outbox into Elasticsearch. At-least-once delivery; ES upserts are
 * idempotent because we key on {@code aggregateId}.
 *
 * <p>For v1 we go DB → ES directly (no RabbitMQ). When a second consumer
 * appears (analytics, notifications) we'll publish to Rabbit and let consumers
 * fan out.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class OutboxPoller {

    private static final int BATCH_SIZE = 100;
    private static final String MEDIA_AGGREGATE = "media";

    private final OutboxRepository outboxRepository;
    private final MediaIndexer mediaIndexer;

    @Scheduled(fixedDelayString = "${flashgif.search.outbox-poll-ms:2000}")
    @Transactional
    public void drain() {
        List<OutboxEvent> batch = outboxRepository.findUnpublished(PageRequest.of(0, BATCH_SIZE));
        if (batch.isEmpty()) return;

        log.debug("Draining {} outbox events", batch.size());
        OffsetDateTime now = OffsetDateTime.now();
        for (OutboxEvent event : batch) {
            try {
                handle(event);
                event.setPublishedAt(now);
            } catch (RuntimeException ex) {
                // Leave published_at null so the next tick retries this row.
                // A real impl would track retry counts; v1 just logs.
                log.error("Failed to handle outbox event {} ({}), will retry",
                        event.getId(), event.getEventType(), ex);
            }
        }
    }

    private void handle(OutboxEvent event) {
        if (!MEDIA_AGGREGATE.equals(event.getAggregateType())) {
            log.debug("Ignoring non-media aggregate {}", event.getAggregateType());
            return;
        }
        UUID mediaId = extractMediaId(event.getPayload(), event.getAggregateId());
        switch (event.getEventType()) {
            case "media.published", "media.updated" -> mediaIndexer.upsert(mediaId);
            case "media.deleted"                    -> mediaIndexer.delete(mediaId);
            default -> log.debug("Ignoring unknown event type {}", event.getEventType());
        }
    }

    private UUID extractMediaId(JsonNode payload, UUID fallback) {
        if (payload != null && payload.has("mediaId")) {
            return UUID.fromString(payload.get("mediaId").asText());
        }
        return fallback;
    }
}
