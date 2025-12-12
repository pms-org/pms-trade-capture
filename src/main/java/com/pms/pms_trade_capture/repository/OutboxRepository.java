package com.pms.pms_trade_capture.repository;

import com.pms.pms_trade_capture.domain.OutboxEvent;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {
    @Query(value = """
        SELECT * FROM outbox_event
        WHERE status = 'PENDING'
        ORDER BY created_at ASC
        LIMIT :limit
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<OutboxEvent> findPendingBatch(int limit);

    @Modifying
    @Transactional
    @Query(value = "UPDATE outbox_event SET status = 'SENT' WHERE id = :id", nativeQuery = true)
    void markSent(Long id);

    @Modifying
    @Transactional
    @Query(value = "UPDATE outbox_event SET attempts = attempts + 1 WHERE id = :id", nativeQuery = true)
    void incrementAttempts(Long id);
}
