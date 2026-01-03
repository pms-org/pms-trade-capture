package com.pms.pms_trade_capture.repository;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

class OutboxRepositoryTest {

    @Test
    void findPendingBatch_queryContainsAdvisoryLock() throws NoSuchMethodException {
        Method m = OutboxRepository.class.getMethod("findPendingBatch", int.class);
        Query q = m.getAnnotation(Query.class);
        assertNotNull(q, "findPendingBatch must declare a @Query");
        assertTrue(q.value().contains("pg_try_advisory_xact_lock"));
    }

    @Test
    void markBatchAsSent_isModifying_andUpdatesOutbox() throws NoSuchMethodException {
        Method m = OutboxRepository.class.getMethod("markBatchAsSent", java.util.List.class);
        Modifying mod = m.getAnnotation(Modifying.class);
        Query q = m.getAnnotation(Query.class);
        assertNotNull(mod, "markBatchAsSent must be @Modifying");
        assertNotNull(q, "markBatchAsSent must declare a @Query");
        assertTrue(q.value().toLowerCase().contains("update outbox_event"));
    }

    @Test
    void markSent_methodExists() throws NoSuchMethodException {
        Method m = OutboxRepository.class.getMethod("markSent", Long.class);
        assertNotNull(m);
    }
}
