package com.fintech.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Entity representing a processed transaction.
 * 
 * This is the source of truth for transaction state.
 * Includes optimistic locking to prevent concurrent modifications.
 */
@Entity
@Table(name = "transactions", indexes = {
    @Index(name = "idx_idempotency_key", columnList = "idempotencyKey", unique = true),
    @Index(name = "idx_from_account", columnList = "fromAccountId"),
    @Index(name = "idx_to_account", columnList = "toAccountId"),
    @Index(name = "idx_created_at", columnList = "createdAt")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionEntity {
    
    @Id
    @Column(columnDefinition = "UUID")
    private UUID transactionId;
    
    @Column(nullable = false, unique = true, length = 255)
    private String idempotencyKey;
    
    @Column(nullable = false, columnDefinition = "UUID")
    private UUID fromAccountId;
    
    @Column(nullable = false, columnDefinition = "UUID")
    private UUID toAccountId;
    
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;
    
    @Column(nullable = false, length = 3)
    private String currency;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType type;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionStatus status;
    
    @Column(length = 500)
    private String failureReason;
    
    @Column(nullable = false)
    private Instant createdAt;
    
    @Column
    private Instant completedAt;
    
    @Version
    private Long version; // Optimistic locking
    
    public enum TransactionType {
        PAYMENT, TRANSFER, REFUND, WITHDRAWAL
    }
    
    public enum TransactionStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED,
        CANCELLED
    }
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (status == null) {
            status = TransactionStatus.PENDING;
        }
    }
}
