package com.pms.pms_trade_capture.outbox;

import com.pms.pms_trade_capture.domain.OutboxEvent;
import com.pms.pms_trade_capture.dto.BatchProcessingResult;
import com.pms.pms_trade_capture.exception.PoisonPillException;
import com.pms.pms_trade_capture.repository.DlqRepository;
import com.pms.pms_trade_capture.repository.OutboxRepository;
import org.junit.jupiter.api.Test;

import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OutboxDispatcherOrderingTest {

    @Test
    void dispatcher_processesSamePortfolioInOrder_andMarksSent() throws InterruptedException {
        OutboxRepository outboxRepo = mock(OutboxRepository.class);
        DlqRepository dlqRepo = mock(DlqRepository.class);
        OutboxEventProcessor processor = mock(OutboxEventProcessor.class);
        AdaptiveBatchSizer sizer = mock(AdaptiveBatchSizer.class);
        TransactionTemplate tx = mock(TransactionTemplate.class);

        // Executor that runs the dispatch loop in a separate thread
        CountDownLatch processed = new CountDownLatch(1);
        Executor executor = r -> new Thread(r).start();

        // Prepare two events for the same portfolio
        UUID portfolio = UUID.randomUUID();
        OutboxEvent e1 = new OutboxEvent(portfolio, UUID.randomUUID(), new byte[] {0x01});
        e1.setId(100L);
        OutboxEvent e2 = new OutboxEvent(portfolio, UUID.randomUUID(), new byte[] {0x02});
        e2.setId(101L);

        when(sizer.getCurrentSize()).thenReturn(10);

        // First call returns the batch, second call returns empty to allow loop to idle
        when(outboxRepo.findPendingBatch(10)).thenReturn(List.of(e1, e2), List.of());

        // TransactionTemplate simply executes the callback
        when(tx.execute(any())).thenAnswer(invocation -> {
            TransactionCallback cb = invocation.getArgument(0);
            return cb.doInTransaction(null);
        });

        when(processor.processBatch(any())).thenAnswer(invocation -> {
            // Signal that processing happened
            processed.countDown();
            return BatchProcessingResult.success(List.of(100L, 101L));
        });

        OutboxDispatcher dispatcher = new OutboxDispatcher(outboxRepo, dlqRepo, processor, sizer, executor, tx);

        dispatcher.start();

        // Wait until processed
        assertTrue(processed.await(2000, java.util.concurrent.TimeUnit.MILLISECONDS));

        // Stop dispatcher to end loop
        dispatcher.stop();

        // Verify processor called with the portfolio batch in the same order
        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(processor, atLeastOnce()).processBatch(captor.capture());
        List<OutboxEvent> processedBatch = captor.getValue();
        assertEquals(2, processedBatch.size());
        assertEquals(100L, processedBatch.get(0).getId());
        assertEquals(101L, processedBatch.get(1).getId());

        // Verify that the successful prefix was marked SENT
        verify(outboxRepo, atLeastOnce()).markBatchAsSent(List.of(100L, 101L));
    }

    @Test
    void dispatcher_processesMultiplePortfoliosIndependently() throws InterruptedException {
        OutboxRepository outboxRepo = mock(OutboxRepository.class);
        DlqRepository dlqRepo = mock(DlqRepository.class);
        OutboxEventProcessor processor = mock(OutboxEventProcessor.class);
        AdaptiveBatchSizer sizer = mock(AdaptiveBatchSizer.class);
        TransactionTemplate tx = mock(TransactionTemplate.class);

        CountDownLatch processed = new CountDownLatch(2);
        Executor executor = r -> new Thread(r).start();

        UUID p1 = UUID.randomUUID(); UUID p2 = UUID.randomUUID();
        OutboxEvent e1 = new OutboxEvent(p1, UUID.randomUUID(), new byte[] {0x01}); e1.setId(1L);
        OutboxEvent e2 = new OutboxEvent(p2, UUID.randomUUID(), new byte[] {0x02}); e2.setId(2L);

        when(sizer.getCurrentSize()).thenReturn(10);
        when(outboxRepo.findPendingBatch(10)).thenReturn(List.of(e1, e2), List.of());
        when(tx.execute(any())).thenAnswer(invocation -> {
            TransactionCallback cb = invocation.getArgument(0);
            return cb.doInTransaction(null);
        });

        when(processor.processBatch(any())).thenAnswer(invocation -> {
            processed.countDown();
            // Return success for each portfolio batch
            List<OutboxEvent> batch = invocation.getArgument(0);
            return BatchProcessingResult.success(batch.stream().map(OutboxEvent::getId).toList());
        });

        OutboxDispatcher dispatcher = new OutboxDispatcher(outboxRepo, dlqRepo, processor, sizer, executor, tx);
        dispatcher.start();

        assertTrue(processed.await(2000, java.util.concurrent.TimeUnit.MILLISECONDS));

        dispatcher.stop();

        // processor called twice (one per portfolio)
        verify(processor, times(2)).processBatch(any());
    }

    @Test
    void idle_resetBatchSizer_onNoWork() throws InterruptedException {
        OutboxRepository outboxRepo = mock(OutboxRepository.class);
        DlqRepository dlqRepo = mock(DlqRepository.class);
        OutboxEventProcessor processor = mock(OutboxEventProcessor.class);
        AdaptiveBatchSizer sizer = mock(AdaptiveBatchSizer.class);
        TransactionTemplate tx = mock(TransactionTemplate.class);

        Executor executor = r -> new Thread(r).start();

        when(sizer.getCurrentSize()).thenReturn(10);
        when(outboxRepo.findPendingBatch(10)).thenReturn(List.of());
        when(tx.execute(any())).thenAnswer(invocation -> {
            TransactionCallback cb = invocation.getArgument(0);
            return cb.doInTransaction(null);
        });

        OutboxDispatcher dispatcher = new OutboxDispatcher(outboxRepo, dlqRepo, processor, sizer, executor, tx);
        dispatcher.start();

        // Give dispatcher a short time to run idle path
        Thread.sleep(200);

        dispatcher.stop();

        verify(sizer, atLeastOnce()).reset();
    }
}
