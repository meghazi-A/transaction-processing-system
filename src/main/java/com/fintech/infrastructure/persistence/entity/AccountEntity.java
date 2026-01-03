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
 * Entity representing a financial account.
 * 
 * Includes optimistic locking to prevent concurrent balance updates.
 */
@Entity
@Table(name = "accounts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountEntity {
    
    @Id
    @Column(columnDefinition = "UUID")
    private UUID accountId;
    
    @Column(nullable = false, length = 100)
    private String accountName;
    
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;
    
    @Column(nullable = false, length = 3)
    private String currency;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountStatus status;
    
    @Column(nullable = false)
    private Instant createdAt;
    
    @Column(nullable = false)
    private Instant updatedAt;
    
    @Version
    private Long version; // Optimistic locking for concurrent updates
    
    public enum AccountStatus {
        ACTIVE, SUSPENDED, CLOSED
    }
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
        if (status == null) {
            status = AccountStatus.ACTIVE;
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
    
    /**
     * Check if account has sufficient balance
     */
    public boolean hasSufficientBalance(BigDecimal amount) {
        return balance.compareTo(amount) >= 0;
    }
    
    /**
     * Debit amount from account
     */
    public void debit(BigDecimal amount) {
        if (!hasSufficientBalance(amount)) {
            throw new IllegalStateException("Insufficient balance");
        }
        this.balance = this.balance.subtract(amount);
    }
    
    /**
     * Credit amount to account
     */
    public void credit(BigDecimal amount) {
        this.balance = this.balance.add(amount);
    }
}
