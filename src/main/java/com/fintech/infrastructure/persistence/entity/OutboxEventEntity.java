package com.fintech.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity representing an outbox event for transactional outbox pattern.
 * 
 * The transactional outbox pattern ensures atomicity between database writes
 * and event publishing. Events are written to this table in the same transaction
 * as business data, then asynchronously published to Kafka.
 * 
 * This solves the dual-write problem:
 * - Without outbox: DB commit succeeds but Kafka publish fails → data inconsistency
 * - With outbox: Both writes in same transaction → atomicity guaranteed
 */
@Entity
@Table(name = "outbox_events", indexes = {
    @Index(name = "idx_status_created", columnList = "status,createdAt")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEventEntity {
    
    @Id
    @Column(columnDefinition = "UUID")
    private UUID eventId;
    
    @Column(nullable = false, length = 50)
    private String eventType;
    
    @Column(nullable = false, length = 100)
    private String aggregateId;
    
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private EventStatus status = EventStatus.PENDING;
    
    @Column(nullable = false)
    private Instant createdAt;
    
    @Column
    private Instant publishedAt;
    
    @Column
    private Integer retryCount;
    
    @Column(length = 500)
    private String errorMessage;
    
    public enum EventStatus {
        PENDING,
        PUBLISHED,
        FAILED
    }
    
    @PrePersist
    protected void onCreate() {
        if (eventId == null) {
            eventId = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (retryCount == null) {
            retryCount = 0;
        }
    }
    
    public void markPublished() {
        this.status = EventStatus.PUBLISHED;
        this.publishedAt = Instant.now();
    }
    
    public void markFailed(String error) {
        this.status = EventStatus.FAILED;
        this.errorMessage = error;
        this.retryCount++;
    }
}
