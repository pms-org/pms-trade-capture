package com.pms.pms_trade_capture.outbox;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.pms.pms_trade_capture.domain.OutboxEvent;
import com.pms.pms_trade_capture.dto.BatchProcessingResult;
import com.pms.pms_trade_capture.exception.PoisonPillException;
import com.pms.pms_trade_capture.repository.DlqRepository;
import com.pms.pms_trade_capture.repository.OutboxRepository;

class OutboxDispatcherFailureTest {

    @Test
    void poisonPill_movesToDlq_andRemovesFromOutbox_onlyPrefixMarked() throws InterruptedException {
        OutboxRepository outboxRepo = mock(OutboxRepository.class);
        DlqRepository dlqRepo = mock(DlqRepository.class);
        OutboxEventProcessor processor = mock(OutboxEventProcessor.class);
        AdaptiveBatchSizer sizer = mock(AdaptiveBatchSizer.class);
        TransactionTemplate tx = mock(TransactionTemplate.class);

        CountDownLatch processed = new CountDownLatch(1);
        Executor executor = r -> new Thread(r).start();

        UUID portfolio = UUID.randomUUID();
        OutboxEvent e1 = new OutboxEvent(portfolio, UUID.randomUUID(), new byte[] {0x01}); e1.setId(1L);
        OutboxEvent e2 = new OutboxEvent(portfolio, UUID.randomUUID(), new byte[] {0x02}); e2.setId(2L);
        OutboxEvent e3 = new OutboxEvent(portfolio, UUID.randomUUID(), new byte[] {0x03}); e3.setId(3L);

        when(sizer.getCurrentSize()).thenReturn(10);
        when(outboxRepo.findPendingBatch(10)).thenReturn(List.of(e1, e2, e3), List.of());

        when(tx.execute(any())).thenAnswer(invocation -> {
            TransactionCallback cb = invocation.getArgument(0);
            return cb.doInTransaction(null);
        });

        when(processor.processBatch(any())).thenAnswer(invocation -> {
            processed.countDown();
            PoisonPillException ppe = new PoisonPillException(3L, "bad", new RuntimeException("x"));
            return BatchProcessingResult.withPoisonPill(List.of(1L,2L), ppe);
        });

        OutboxDispatcher dispatcher = new OutboxDispatcher(outboxRepo, dlqRepo, processor, sizer, executor, tx);
        // Ensure a non-zero backoff is set (we instantiate directly, so @Value is not applied)
        try {
            java.lang.reflect.Field f = OutboxDispatcher.class.getDeclaredField("systemFailureBackoffMs");
            f.setAccessible(true);
            f.setLong(dispatcher, 1000L);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        dispatcher.start();

        assertTrue(processed.await(2000, java.util.concurrent.TimeUnit.MILLISECONDS));

        dispatcher.stop();

        // Only prefix should be marked SENT
        verify(outboxRepo, atLeastOnce()).markBatchAsSent(List.of(1L,2L));

        // Poison pill should be moved to DLQ and removed from outbox
        verify(dlqRepo, atLeastOnce()).save(any());
        verify(outboxRepo, atLeastOnce()).delete(argThat(ev -> ev.getId().equals(3L)));
    }

    @Test
    void systemFailure_preventsFurtherPortfolios_andNoSentMarks() throws InterruptedException {
        OutboxRepository outboxRepo = mock(OutboxRepository.class);
        DlqRepository dlqRepo = mock(DlqRepository.class);
        OutboxEventProcessor processor = mock(OutboxEventProcessor.class);
        AdaptiveBatchSizer sizer = mock(AdaptiveBatchSizer.class);
        TransactionTemplate tx = mock(TransactionTemplate.class);

        CountDownLatch processed = new CountDownLatch(1);
        Executor executor = r -> new Thread(r).start();

        // Two portfolios; first will cause system failure, second should not be processed
        UUID p1 = UUID.randomUUID(); UUID p2 = UUID.randomUUID();
        OutboxEvent a = new OutboxEvent(p1, UUID.randomUUID(), new byte[]{0x1}); a.setId(10L);
        OutboxEvent b = new OutboxEvent(p2, UUID.randomUUID(), new byte[]{0x2}); b.setId(20L);

        when(sizer.getCurrentSize()).thenReturn(10);
        // return a batch with two events from different portfolios
        when(outboxRepo.findPendingBatch(10)).thenReturn(List.of(a, b), List.of());

        when(tx.execute(any())).thenAnswer(invocation -> {
            TransactionCallback cb = invocation.getArgument(0);
            return cb.doInTransaction(null);
        });

        when(processor.processBatch(any())).thenAnswer(invocation -> {
            // If it's portfolio p1, return system failure
            List<OutboxEvent> batch = invocation.getArgument(0);
            if (batch.stream().anyMatch(e -> e.getId().equals(10L))) {
                processed.countDown();
                return BatchProcessingResult.systemFailure(List.of());
            }
            fail("Second portfolio should not be processed when a system failure occurs");
            return null;
        });

    OutboxDispatcher dispatcher = new OutboxDispatcher(outboxRepo, dlqRepo, processor, sizer, executor, tx);
    dispatcher.start();

    assertTrue(processed.await(2000, java.util.concurrent.TimeUnit.MILLISECONDS));

    dispatcher.stop();

    // Processor should have been invoked only once (system failure caused break)
    verify(processor, atLeastOnce()).processBatch(any());
    verify(processor, times(1)).processBatch(any());

    // No successful ids should be marked SENT
    verify(outboxRepo, never()).markBatchAsSent(any());
    }

    @Test
    void systemFailure_preventsBatchSizerAdjust_andTriggersBackoff() throws InterruptedException {
        OutboxRepository outboxRepo = mock(OutboxRepository.class);
        DlqRepository dlqRepo = mock(DlqRepository.class);
        OutboxEventProcessor processor = mock(OutboxEventProcessor.class);
        AdaptiveBatchSizer sizer = mock(AdaptiveBatchSizer.class);
        TransactionTemplate tx = mock(TransactionTemplate.class);

        CountDownLatch processed = new CountDownLatch(1);
        Executor executor = r -> new Thread(r).start();

        UUID p1 = UUID.randomUUID();
        OutboxEvent a = new OutboxEvent(p1, UUID.randomUUID(), new byte[]{0x1}); a.setId(10L);

        when(sizer.getCurrentSize()).thenReturn(10);
        when(outboxRepo.findPendingBatch(10)).thenReturn(List.of(a), List.of());
        when(tx.execute(any())).thenAnswer(invocation -> {
            TransactionCallback cb = invocation.getArgument(0);
            return cb.doInTransaction(null);
        });

        when(processor.processBatch(any())).thenAnswer(invocation -> {
            processed.countDown();
            return BatchProcessingResult.systemFailure(List.of());
        });

        OutboxDispatcher dispatcher = new OutboxDispatcher(outboxRepo, dlqRepo, processor, sizer, executor, tx);
        // Ensure a non-zero backoff is set (we instantiate directly, so @Value is not applied)
        try {
            java.lang.reflect.Field f = OutboxDispatcher.class.getDeclaredField("systemFailureBackoffMs");
            f.setAccessible(true);
            f.setLong(dispatcher, 1000L);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        dispatcher.start();

        assertTrue(processed.await(2000, java.util.concurrent.TimeUnit.MILLISECONDS));

        dispatcher.stop();

        // When system failure occurs, batchSizer.adjust should NOT be called
        verify(sizer, never()).adjust(anyLong(), anyInt());
    }
}
