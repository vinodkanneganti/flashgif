package com.flashgif.infra.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Façade for writing outbox events. Callers stay in their own @Transactional
 * boundary; this just inserts into the outbox table within that same tx.
 */
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private final OutboxRepository repository;
    private final ObjectMapper objectMapper;

    public void publish(String aggregateType, UUID aggregateId, String eventType, Object payload) {
        JsonNode json = objectMapper.valueToTree(payload);
        repository.save(OutboxEvent.of(aggregateType, aggregateId, eventType, json));
    }
}
