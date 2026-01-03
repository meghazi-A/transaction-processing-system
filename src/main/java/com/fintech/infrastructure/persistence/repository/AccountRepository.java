package com.fintech.infrastructure.persistence.repository;

import com.fintech.infrastructure.persistence.entity.AccountEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<AccountEntity, UUID> {
    
    /**
     * Find account with pessimistic write lock.
     * 
     * This prevents concurrent modifications to the same account,
     * avoiding race conditions in balance updates.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AccountEntity> findByAccountId(UUID accountId);
}
