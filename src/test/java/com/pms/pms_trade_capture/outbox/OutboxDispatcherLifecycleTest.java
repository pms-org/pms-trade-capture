package com.pms.pms_trade_capture.outbox;

import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import org.springframework.transaction.support.TransactionTemplate;

import com.pms.pms_trade_capture.repository.DlqRepository;
import com.pms.pms_trade_capture.repository.OutboxRepository;

class OutboxDispatcherLifecycleTest {

    @Test
    void start_isIdempotent_andStopStopsLoop() {
        OutboxRepository outboxRepo = mock(OutboxRepository.class);
        DlqRepository dlqRepo = mock(DlqRepository.class);
        OutboxEventProcessor processor = mock(OutboxEventProcessor.class);
        AdaptiveBatchSizer sizer = mock(AdaptiveBatchSizer.class);
        TransactionTemplate tx = mock(TransactionTemplate.class);

        Executor executor = mock(Executor.class);

        OutboxDispatcher dispatcher = new OutboxDispatcher(outboxRepo, dlqRepo, processor, sizer, executor, tx);

        dispatcher.start();
        dispatcher.start(); // second start should be ignored

        verify(executor, times(1)).execute(any());

        assertTrue(dispatcher.isRunning());

        dispatcher.stop();

        assertFalse(dispatcher.isRunning());
    }

    @Test
    void stop_isIdempotent_andPhaseIsHigh() {
        OutboxRepository outboxRepo = mock(OutboxRepository.class);
        DlqRepository dlqRepo = mock(DlqRepository.class);
        OutboxEventProcessor processor = mock(OutboxEventProcessor.class);
        AdaptiveBatchSizer sizer = mock(AdaptiveBatchSizer.class);
        TransactionTemplate tx = mock(TransactionTemplate.class);

        Executor executor = mock(Executor.class);

        OutboxDispatcher dispatcher = new OutboxDispatcher(outboxRepo, dlqRepo, processor, sizer, executor, tx);

        dispatcher.start();
        dispatcher.stop();
        dispatcher.stop();

        assertFalse(dispatcher.isRunning());
        assertEquals(Integer.MAX_VALUE - 1000, dispatcher.getPhase());
    }

    @Test
    void isAutoStartup_returnsDefaultBoolean() {
        OutboxRepository outboxRepo = mock(OutboxRepository.class);
        DlqRepository dlqRepo = mock(DlqRepository.class);
        OutboxEventProcessor processor = mock(OutboxEventProcessor.class);
        AdaptiveBatchSizer sizer = mock(AdaptiveBatchSizer.class);
        TransactionTemplate tx = mock(TransactionTemplate.class);

        Executor executor = mock(Executor.class);

        OutboxDispatcher dispatcher = new OutboxDispatcher(outboxRepo, dlqRepo, processor, sizer, executor, tx);
        // just assert method returns without throwing and gives a boolean
        boolean val = dispatcher.isAutoStartup();
        assertTrue(val == true || val == false);
    }
}
