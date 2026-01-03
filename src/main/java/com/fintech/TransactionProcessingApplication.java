package com.fintech;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Event-Driven Financial Transaction Processing System
 * 
 * Production-grade transaction processing with exactly-once semantics.
 * 
 * Architecture:
 * - Kafka-based event processing
 * - Idempotent consumers (duplicate detection)
 * - Manual offset management (at-least-once delivery)
 * - Transactional outbox pattern (atomic event publishing)
 * - Circuit breaker for external dependencies
 * - Dead Letter Queue for failed events
 * 
 * Scale Targets:
 * - 500 TPS sustained (2,000 TPS peak)
 * - 40M transactions/day
 * - 99.99% availability
 * - Zero duplicate processing (exactly-once semantics)
 */
@SpringBootApplication
@EnableTransactionManagement
@EnableScheduling
public class TransactionProcessingApplication {

    public static void main(String[] args) {
        SpringApplication.run(TransactionProcessingApplication.class, args);
    }
}
