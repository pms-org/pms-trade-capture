package com.pms.pms_trade_capture.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.pms.pms_trade_capture.domain.OutboxEvent;

@Repository
public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * Fetches pending events using advisory locks to ensure portfolio isolation.
     * Only returns events from portfolios that this instance successfully locked.
     *
     * @param limit maximum number of events to fetch
     * @return list of events from locked portfolios, ordered by creation time
     */
    @Query(value = """
            SELECT * FROM outbox_event
            WHERE status = 'PENDING'
            AND pg_try_advisory_xact_lock(hashtext(portfolio_id::text))
            ORDER BY created_at ASC, id ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<OutboxEvent> findPendingBatch(@Param("limit") int limit);

    /**
     * Marks a single event as sent. Used for backward compatibility.
     */
    @Modifying
    @Transactional
    @Query(value = "UPDATE outbox_event SET status = 'SENT' WHERE id = :id", nativeQuery = true)
    void markSent(Long id);

    /**
     * Marks multiple events as sent in a single batch update.
     * Must be called within a transaction.
     */
    @Modifying
    @Query(value = "UPDATE outbox_event SET status = 'SENT', sent_at = CURRENT_TIMESTAMP WHERE id IN (:ids)", nativeQuery = true)
    void markBatchAsSent(@Param("ids") List<Long> ids);

    /**
     * Increments retry attempts for an event. Deprecated - avoid N+1 query patterns.
     */
    @Deprecated
    @Modifying
    @Transactional
    @Query(value = "UPDATE outbox_event SET attempts = attempts + 1 WHERE id = :id", nativeQuery = true)
    void incrementAttempts(Long id);
}
