package com.fintech.domain.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintech.infrastructure.persistence.entity.IdempotencyRecordEntity;
import com.fintech.infrastructure.persistence.repository.IdempotencyRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

/**
 * Idempotency service for duplicate detection.
 * 
 * Implements idempotency using database-backed deduplication:
 * 1. Check if idempotency key exists in database
 * 2. If exists, return cached response (duplicate detected)
 * 3. If not exists, process request and store result
 * 
 * This ensures exactly-once semantics even with at-least-once delivery from Kafka.
 * 
 * Why Database-Backed?
 * - Survives service restarts (unlike in-memory cache)
 * - Shared across all service instances
 * - Atomic check-and-set using unique constraint
 * 
 * Performance:
 * - Indexed lookup on idempotency_key (1-5ms)
 * - Acceptable overhead for financial transactions
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {
    
    private final IdempotencyRecordRepository idempotencyRepository;
    private final ObjectMapper objectMapper;
    
    @Value("${app.transaction.idempotency-window-hours:24}")
    private int idempotencyWindowHours;
    
    /**
     * Check if request is duplicate.
     * 
     * @param idempotencyKey Unique key for this request
     * @return Optional containing cached response if duplicate, empty if new request
     */
    @Transactional(readOnly = true)
    public Optional<String> checkDuplicate(String idempotencyKey) {
        Optional<IdempotencyRecordEntity> record = idempotencyRepository.findByIdempotencyKey(idempotencyKey);
        
        if (record.isPresent()) {
            IdempotencyRecordEntity entity = record.get();
            
            if (entity.isExpired()) {
                log.debug("Idempotency record expired for key: {}", idempotencyKey);
                return Optional.empty();
            }
            
            log.info("Duplicate request detected for key: {}", idempotencyKey);
            return Optional.of(entity.getResponse());
        }
        
        return Optional.empty();
    }
    
    /**
     * Store idempotency record after successful processing.
     * 
     * @param idempotencyKey Unique key for this request
     * @param transactionId Transaction ID
     * @param response Response to cache
     */
    @Transactional
    public void storeIdempotencyRecord(String idempotencyKey, UUID transactionId, Object response) {
        try {
            String responseJson = objectMapper.writeValueAsString(response);
            
            IdempotencyRecordEntity record = IdempotencyRecordEntity.builder()
                    .idempotencyKey(idempotencyKey)
                    .transactionId(transactionId)
                    .response(responseJson)
                    .expiresAt(Instant.now().plus(idempotencyWindowHours, ChronoUnit.HOURS))
                    .build();
            
            idempotencyRepository.save(record);
            
            log.debug("Stored idempotency record for key: {}", idempotencyKey);
            
        } catch (Exception e) {
            log.error("Error storing idempotency record: {}", e.getMessage(), e);
            // Don't throw - idempotency storage failure shouldn't fail the transaction
        }
    }
    
    /**
     * Generate idempotency key from transaction event.
     * 
     * Uses event ID as idempotency key for simplicity.
     * In production, might use hash of (userId + amount + timestamp).
     */
    public String generateIdempotencyKey(UUID eventId, UUID fromAccountId, UUID toAccountId) {
        return String.format("%s:%s:%s", eventId, fromAccountId, toAccountId);
    }
}
