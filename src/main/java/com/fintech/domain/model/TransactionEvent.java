package com.fintech.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Domain model representing a transaction event.
 * 
 * This is the input event that triggers transaction processing.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionEvent {
    
    private UUID eventId;
    private UUID transactionId;
    private UUID fromAccountId;
    private UUID toAccountId;
    private BigDecimal amount;
    private String currency;
    private TransactionType type;
    private Instant timestamp;
    private String idempotencyKey;
    
    public enum TransactionType {
        PAYMENT,
        TRANSFER,
        REFUND,
        WITHDRAWAL
    }
}
