package com.fintech.api;

import com.fintech.domain.model.TransactionEvent;
import com.fintech.domain.service.TransactionProcessor;
import com.fintech.infrastructure.persistence.entity.TransactionEntity;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST API for transaction processing.
 * 
 * Provides HTTP endpoint for transaction submission (in addition to Kafka ingestion).
 * Useful for testing and synchronous transaction requests.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {
    
    private final TransactionProcessor transactionProcessor;
    
    /**
     * Submit a transaction for processing.
     * 
     * POST /api/v1/transactions
     * 
     * Request body: TransactionEvent
     * Response: TransactionEntity
     */
    @PostMapping
    public ResponseEntity<TransactionEntity> submitTransaction(@Valid @RequestBody TransactionEvent event) {
        log.info("Received transaction request: {}", event.getTransactionId());
        
        TransactionEntity result = transactionProcessor.processTransaction(event);
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * Health check endpoint.
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }
}
