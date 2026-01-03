package com.fintech.domain.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintech.domain.model.TransactionEvent;
import com.fintech.infrastructure.persistence.entity.AccountEntity;
import com.fintech.infrastructure.persistence.entity.OutboxEventEntity;
import com.fintech.infrastructure.persistence.entity.TransactionEntity;
import com.fintech.infrastructure.persistence.repository.AccountRepository;
import com.fintech.infrastructure.persistence.repository.OutboxEventRepository;
import com.fintech.infrastructure.persistence.repository.TransactionRepository;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Transaction processor with ACID guarantees.
 * 
 * Processing Flow:
 * 1. Check idempotency (duplicate detection)
 * 2. Validate accounts and balances
 * 3. Lock accounts (pessimistic locking)
 * 4. Debit source account
 * 5. Credit destination account
 * 6. Create transaction record
 * 7. Create outbox event (for downstream notification)
 * 8. Commit transaction (all or nothing)
 * 
 * ACID Properties:
 * - Atomicity: All steps in single transaction (commit or rollback)
 * - Consistency: Balance invariants maintained
 * - Isolation: Pessimistic locking prevents concurrent modifications
 * - Durability: PostgreSQL ensures persistence
 * 
 * Failure Handling:
 * - Insufficient balance: Transaction rejected, no state change
 * - Account not found: Transaction rejected
 * - Database deadlock: Retry with exponential backoff
 * - Optimistic lock failure: Retry
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionProcessor {
    
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    
    /**
     * Process transaction with retry on transient failures.
     * 
     * Retry handles:
     * - OptimisticLockingFailureException (concurrent updates)
     * - DeadlockLoserDataAccessException (database deadlocks)
     */
    @Transactional
    @Retry(name = "transactionProcessing")
    public TransactionEntity processTransaction(TransactionEvent event) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            // Step 1: Check idempotency
            String idempotencyKey = event.getIdempotencyKey();
            Optional<String> cachedResponse = idempotencyService.checkDuplicate(idempotencyKey);
            
            if (cachedResponse.isPresent()) {
                log.info("Duplicate transaction detected: {}", event.getTransactionId());
                
                Counter.builder("transaction.processed")
                        .tag("result", "duplicate")
                        .register(meterRegistry)
                        .increment();
                
                // Return cached result
                TransactionEntity cached = objectMapper.readValue(
                        cachedResponse.get(), 
                        TransactionEntity.class
                );
                return cached;
            }
            
            // Step 2: Validate and lock accounts
            AccountEntity fromAccount = accountRepository.findByAccountId(event.getFromAccountId())
                    .orElseThrow(() -> new IllegalArgumentException("Source account not found: " + event.getFromAccountId()));
            
            AccountEntity toAccount = accountRepository.findByAccountId(event.getToAccountId())
                    .orElseThrow(() -> new IllegalArgumentException("Destination account not found: " + event.getToAccountId()));
            
            // Step 3: Validate business rules
            if (fromAccount.getStatus() != AccountEntity.AccountStatus.ACTIVE) {
                throw new IllegalStateException("Source account is not active");
            }
            
            if (toAccount.getStatus() != AccountEntity.AccountStatus.ACTIVE) {
                throw new IllegalStateException("Destination account is not active");
            }
            
            if (!fromAccount.hasSufficientBalance(event.getAmount())) {
                throw new IllegalStateException("Insufficient balance");
            }
            
            // Step 4: Execute transaction
            fromAccount.debit(event.getAmount());
            toAccount.credit(event.getAmount());
            
            accountRepository.save(fromAccount);
            accountRepository.save(toAccount);
            
            // Step 5: Create transaction record
            TransactionEntity transaction = TransactionEntity.builder()
                    .transactionId(event.getTransactionId())
                    .idempotencyKey(idempotencyKey)
                    .fromAccountId(event.getFromAccountId())
                    .toAccountId(event.getToAccountId())
                    .amount(event.getAmount())
                    .currency(event.getCurrency())
                    .type(TransactionEntity.TransactionType.valueOf(event.getType().name()))
                    .status(TransactionEntity.TransactionStatus.COMPLETED)
                    .completedAt(Instant.now())
                    .build();
            
            transaction = transactionRepository.save(transaction);
            
            // Step 6: Create outbox event (transactional outbox pattern)
            OutboxEventEntity outboxEvent = OutboxEventEntity.builder()
                    .eventType("TRANSACTION_COMPLETED")
                    .aggregateId(transaction.getTransactionId().toString())
                    .payload(objectMapper.writeValueAsString(transaction))
                    .build();
            
            outboxEventRepository.save(outboxEvent);
            
            // Step 7: Store idempotency record
            idempotencyService.storeIdempotencyRecord(idempotencyKey, transaction.getTransactionId(), transaction);
            
            // Record metrics
            sample.stop(Timer.builder("transaction.processing.latency")
                    .tag("type", event.getType().name())
                    .register(meterRegistry));
            
            Counter.builder("transaction.processed")
                    .tag("result", "success")
                    .tag("type", event.getType().name())
                    .register(meterRegistry)
                    .increment();
            
            log.info("Transaction processed successfully: {} (amount: {}, from: {}, to: {})",
                    transaction.getTransactionId(), event.getAmount(), 
                    event.getFromAccountId(), event.getToAccountId());
            
            return transaction;
            
        } catch (IllegalArgumentException | IllegalStateException e) {
            // Business validation failures
            log.warn("Transaction validation failed: {}", e.getMessage());
            
            Counter.builder("transaction.processed")
                    .tag("result", "validation_failed")
                    .register(meterRegistry)
                    .increment();
            
            // Create failed transaction record
            TransactionEntity failedTransaction = TransactionEntity.builder()
                    .transactionId(event.getTransactionId())
                    .idempotencyKey(event.getIdempotencyKey())
                    .fromAccountId(event.getFromAccountId())
                    .toAccountId(event.getToAccountId())
                    .amount(event.getAmount())
                    .currency(event.getCurrency())
                    .type(TransactionEntity.TransactionType.valueOf(event.getType().name()))
                    .status(TransactionEntity.TransactionStatus.FAILED)
                    .failureReason(e.getMessage())
                    .build();
            
            return transactionRepository.save(failedTransaction);
            
        } catch (Exception e) {
            log.error("Error processing transaction {}: {}", event.getTransactionId(), e.getMessage(), e);
            
            Counter.builder("transaction.processed")
                    .tag("result", "error")
                    .register(meterRegistry)
                    .increment();
            
            throw new RuntimeException("Transaction processing failed", e);
        }
    }
}
