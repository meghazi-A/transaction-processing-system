package com.fintech.domain.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintech.domain.model.TransactionEvent;
import com.fintech.infrastructure.persistence.entity.AccountEntity;
import com.fintech.infrastructure.persistence.entity.TransactionEntity;
import com.fintech.infrastructure.persistence.repository.AccountRepository;
import com.fintech.infrastructure.persistence.repository.OutboxEventRepository;
import com.fintech.infrastructure.persistence.repository.TransactionRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionProcessorTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private IdempotencyService idempotencyService;

    private MeterRegistry meterRegistry;
    private ObjectMapper objectMapper;
    private TransactionProcessor transactionProcessor;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        objectMapper = new ObjectMapper().findAndRegisterModules();


        transactionProcessor = new TransactionProcessor(
                transactionRepository,
                accountRepository,
                outboxEventRepository,
                idempotencyService,
                objectMapper,
                meterRegistry
        );

        
    }

    @Test
    void processTransaction_success() {
        TransactionEvent event = createTestEvent();

        AccountEntity from = AccountEntity.builder()
                .accountId(event.getFromAccountId())
                .accountName("From Account")
                .balance(new BigDecimal("500.00"))
                .currency("USD")
                .status(AccountEntity.AccountStatus.ACTIVE)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        AccountEntity to = AccountEntity.builder()
                .accountId(event.getToAccountId())
                .accountName("To Account")
                .balance(new BigDecimal("100.00"))
                .currency("USD")
                .status(AccountEntity.AccountStatus.ACTIVE)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
	

	when(transactionRepository.save(any(TransactionEntity.class))).thenAnswer(i -> i.getArgument(0));
	when(accountRepository.save(any(AccountEntity.class))).thenAnswer(i -> i.getArgument(0));
        when(idempotencyService.checkDuplicate(event.getIdempotencyKey())).thenReturn(Optional.empty());
        when(accountRepository.findByAccountId(event.getFromAccountId())).thenReturn(Optional.of(from));
        when(accountRepository.findByAccountId(event.getToAccountId())).thenReturn(Optional.of(to));

        TransactionEntity result = transactionProcessor.processTransaction(event);

        assertNotNull(result);
        assertEquals(TransactionEntity.TransactionStatus.COMPLETED, result.getStatus());

        // BigDecimal safe compare
        assertEquals(0, from.getBalance().compareTo(new BigDecimal("400.00")));
        assertEquals(0, to.getBalance().compareTo(new BigDecimal("200.00")));

        verify(transactionRepository).save(any(TransactionEntity.class));
        verify(outboxEventRepository).save(any());
        verify(idempotencyService).storeIdempotencyRecord(eq(event.getIdempotencyKey()), eq(event.getTransactionId()), any(TransactionEntity.class));
    }

    @Test
    void processTransaction_duplicate_returnsCached_andDoesNotWrite() throws Exception {
        TransactionEvent event = createTestEvent();

        TransactionEntity cachedTxn = TransactionEntity.builder()
                .transactionId(event.getTransactionId())
                .idempotencyKey(event.getIdempotencyKey())
                .fromAccountId(event.getFromAccountId())
                .toAccountId(event.getToAccountId())
                .amount(event.getAmount())
                .currency(event.getCurrency())
                .type(TransactionEntity.TransactionType.valueOf(event.getType().name()))
                .status(TransactionEntity.TransactionStatus.COMPLETED)
                .completedAt(Instant.now())
                .build();

        String cachedJson = objectMapper.writeValueAsString(cachedTxn);

        when(idempotencyService.checkDuplicate(event.getIdempotencyKey()))
                .thenReturn(Optional.of(cachedJson));

        TransactionEntity result = transactionProcessor.processTransaction(event);

        assertNotNull(result);
        assertEquals(TransactionEntity.TransactionStatus.COMPLETED, result.getStatus());
        assertEquals(event.getTransactionId(), result.getTransactionId());

        verify(transactionRepository, never()).save(any());
        verify(accountRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
        verify(idempotencyService, never()).storeIdempotencyRecord(anyString(), any(UUID.class), any());
    }

    @Test
    void processTransaction_insufficientBalance_fails_andSavesFailedTxn() {
        TransactionEvent event = createTestEvent();

        AccountEntity from = AccountEntity.builder()
                .accountId(event.getFromAccountId())
                .accountName("From Account")
                .balance(new BigDecimal("50.00")) // less than 100
                .currency("USD")
                .status(AccountEntity.AccountStatus.ACTIVE)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        AccountEntity to = AccountEntity.builder()
                .accountId(event.getToAccountId())
                .accountName("To Account")
                .balance(new BigDecimal("100.00"))
                .currency("USD")
                .status(AccountEntity.AccountStatus.ACTIVE)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

	when(transactionRepository.save(any(TransactionEntity.class))).thenAnswer(i -> i.getArgument(0));
        when(idempotencyService.checkDuplicate(event.getIdempotencyKey())).thenReturn(Optional.empty());
        when(accountRepository.findByAccountId(event.getFromAccountId())).thenReturn(Optional.of(from));
        when(accountRepository.findByAccountId(event.getToAccountId())).thenReturn(Optional.of(to));

        TransactionEntity result = transactionProcessor.processTransaction(event);

        assertNotNull(result);
        assertEquals(TransactionEntity.TransactionStatus.FAILED, result.getStatus());
        assertNotNull(result.getFailureReason());
        assertTrue(result.getFailureReason().toLowerCase().contains("insufficient"));

        // balances should remain unchanged
        assertEquals(0, from.getBalance().compareTo(new BigDecimal("50.00")));
        assertEquals(0, to.getBalance().compareTo(new BigDecimal("100.00")));

        verify(transactionRepository).save(any(TransactionEntity.class));
        verify(outboxEventRepository, never()).save(any());
        verify(idempotencyService, never()).storeIdempotencyRecord(anyString(), any(UUID.class), any());
    }

    

    private TransactionEvent createTestEvent() {
        UUID fromId = UUID.randomUUID();
        UUID toId = UUID.randomUUID();

        return TransactionEvent.builder()
                .eventId(UUID.randomUUID())
                .transactionId(UUID.randomUUID())
                .fromAccountId(fromId)
                .toAccountId(toId)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .type(TransactionEvent.TransactionType.PAYMENT)
                .timestamp(Instant.now())
                .idempotencyKey("txn-test-001")
                .build();
    }
}
