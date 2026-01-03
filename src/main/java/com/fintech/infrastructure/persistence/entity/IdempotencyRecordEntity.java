package com.fintech.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity for idempotency tracking.
 * 
 * Prevents duplicate processing of the same transaction event.
 * Uses unique constraint on idempotency_key to detect duplicates at database level.
 * 
 * This is critical for exactly-once semantics in distributed systems.
 */
@Entity
@Table(name = "idempotency_records", indexes = {
    @Index(name = "idx_idempotency_key", columnList = "idempotencyKey", unique = true),
    @Index(name = "idx_created_at", columnList = "createdAt")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdempotencyRecordEntity {
    
    @Id
    @Column(columnDefinition = "UUID")
    private UUID recordId;
    
    @Column(nullable = false, unique = true, length = 255)
    private String idempotencyKey;
    
    @Column(nullable = false, columnDefinition = "UUID")
    private UUID transactionId;
    
    @Column(nullable = false, columnDefinition = "TEXT")
    private String response;
    
    @Column(nullable = false)
    private Instant createdAt;
    
    @Column(nullable = false)
    private Instant expiresAt;
    
    @PrePersist
    protected void onCreate() {
        if (recordId == null) {
            recordId = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
    
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
