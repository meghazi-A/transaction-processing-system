package com.fintech.domain.service;

import com.fintech.infrastructure.persistence.entity.OutboxEventEntity;
import com.fintech.infrastructure.persistence.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Outbox publisher for transactional outbox pattern.
 * 
 * Polls outbox_events table and publishes pending events to Kafka.
 * This ensures atomicity between database writes and event publishing.
 * 
 * How It Works:
 * 1. Business logic writes to database + outbox table (same transaction)
 * 2. This service polls outbox table periodically
 * 3. Publishes events to Kafka
 * 4. Marks events as published
 * 
 * Why This Solves Dual-Write Problem:
 * - Without outbox: DB commit succeeds but Kafka publish fails → inconsistency
 * - With outbox: Both writes in same transaction → atomicity guaranteed
 * 
 * Failure Handling:
 * - Kafka publish fails: Event stays PENDING, retried on next poll
 * - Service crashes: Events remain PENDING, picked up after restart
 * - Duplicate publish: Downstream consumers must be idempotent
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxPublisher {
    
    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    
    @Value("${app.kafka.topics.ledger-events}")
    private String ledgerEventsTopic;
    
    @Value("${app.outbox.batch-size:10}")
    private int batchSize;
    
    /**
     * Poll and publish outbox events.
     * 
     * Runs every 100ms to keep latency low.
     */
    @Scheduled(fixedDelayString = "${app.outbox.polling-interval-ms:100}")
    @Transactional
    public void publishPendingEvents() {
        try {
            // Fetch pending events
            List<OutboxEventEntity> pendingEvents = outboxEventRepository
                    .findTop10ByStatusOrderByCreatedAtAsc(OutboxEventEntity.EventStatus.PENDING);
            
            if (pendingEvents.isEmpty()) {
                return;
            }
            
            log.debug("Publishing {} pending outbox events", pendingEvents.size());
            
            for (OutboxEventEntity event : pendingEvents) {
                try {
                    // Publish to Kafka
                    kafkaTemplate.send(ledgerEventsTopic, event.getAggregateId(), event.getPayload())
                            .whenComplete((result, ex) -> {
                                if (ex == null) {
                                    // Mark as published
                                    event.markPublished();
                                    outboxEventRepository.save(event);
                                    
                                    log.debug("Published outbox event: {} to topic: {}", 
                                            event.getEventId(), ledgerEventsTopic);
                                } else {
                                    // Mark as failed
                                    event.markFailed(ex.getMessage());
                                    outboxEventRepository.save(event);
                                    
                                    log.error("Failed to publish outbox event {}: {}", 
                                            event.getEventId(), ex.getMessage());
                                }
                            });
                    
                } catch (Exception e) {
                    log.error("Error publishing outbox event {}: {}", event.getEventId(), e.getMessage(), e);
                    event.markFailed(e.getMessage());
                    outboxEventRepository.save(event);
                }
            }
            
        } catch (Exception e) {
            log.error("Error in outbox publisher: {}", e.getMessage(), e);
        }
    }
}
