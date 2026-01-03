package com.fintech.infrastructure.persistence.repository;

import com.fintech.infrastructure.persistence.entity.OutboxEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {
    
    List<OutboxEventEntity> findTop10ByStatusOrderByCreatedAtAsc(OutboxEventEntity.EventStatus status);
}
