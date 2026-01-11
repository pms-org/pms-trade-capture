package com.pms.pms_trade_capture.outbox;

import java.util.List;
import java.util.concurrent.Executor;

import com.pms.pms_trade_capture.dto.BatchProcessingResult;
import com.pms.pms_trade_capture.exception.PoisonPillException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.pms.pms_trade_capture.domain.DlqEntry;
import com.pms.pms_trade_capture.domain.OutboxEvent;
import com.pms.pms_trade_capture.repository.DlqRepository;
import com.pms.pms_trade_capture.repository.OutboxRepository;

import lombok.SneakyThrows;

/**
 * Outbox dispatcher that ensures strict ordering of events per portfolio using PostgreSQL advisory locks.
 * Processes events in portfolio-isolated batches to guarantee chronological order and prevent message reordering.
 */
@Component
public class OutboxDispatcher implements SmartLifecycle {
    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);

    private final OutboxRepository outboxRepo;
    private final DlqRepository dlqRepo;
    private final OutboxEventProcessor processor;
    private final AdaptiveBatchSizer batchSizer;
    private final Executor taskExecutor;
    private final TransactionTemplate transactionTemplate;

    @Value("${app.outbox.system-failure-backoff-ms:1000}")
    private long systemFailureBackoffMs;

    @Value("${app.outbox.max-backoff-ms:30000}")
    private long maxBackoffMs;

    private volatile boolean running = false;
    private volatile long currentBackoff = 0;

    public OutboxDispatcher(OutboxRepository outboxRepo,
            DlqRepository dlqRepo,
            OutboxEventProcessor processor,
            AdaptiveBatchSizer batchSizer,
            @Qualifier("outboxExecutor") Executor taskExecutor,
            TransactionTemplate transactionTemplate) {
        this.outboxRepo = outboxRepo;
        this.processor = processor;
        this.dlqRepo = dlqRepo;
        this.batchSizer = batchSizer;
        this.taskExecutor = taskExecutor;
        this.transactionTemplate = transactionTemplate;
    }

    @SneakyThrows
    @Override
    public void start() {
        if (running)
            return;
        log.info("------------Starting Portfolio-Ordered Outbox Dispatcher...---------------");
        running = true;

        // Submit the long-running loop to the Spring-managed thread pool
        taskExecutor.execute(this::dispatchLoop);
    }

    @Override
    public void stop() {
        log.info("-------------------Stopping Outbox Dispatcher...-----------------------");
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean isAutoStartup() {
        return SmartLifecycle.super.isAutoStartup();
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 1000;
    }

    /**
     * Main dispatch loop that processes outbox events using advisory locks for portfolio isolation.
     * Groups events by portfolio to maintain ordering and processes each portfolio's batch safely.
     */
    private void dispatchLoop() {
        while (running) {
            try {
                // Apply backoff if previous iteration had system failure
                if (currentBackoff > 0) {
                    log.warn("System failure backoff active: sleeping {}ms", currentBackoff);
                    sleep(currentBackoff);
                }

                long startTime = System.currentTimeMillis();

                // STEP 1: Fetch batch with advisory lock-based portfolio isolation
                int limit = batchSizer.getCurrentSize();
                List<OutboxEvent> batch = transactionTemplate.execute(status -> outboxRepo.findPendingBatch(limit));

                if (batch == null || batch.isEmpty()) {
                    // No work to do (or all portfolios locked by other pods)
                    batchSizer.reset();
                    currentBackoff = 0; // Reset backoff on idle
                    sleep(50);
                    continue;
                }

                // STEP 2: Group by portfolio (maintains insertion order)
                // Note: All events in batch are from portfolios THIS pod locked
                var eventsByPortfolio = new java.util.LinkedHashMap<java.util.UUID, java.util.ArrayList<OutboxEvent>>();
                for (OutboxEvent event : batch) {
                    eventsByPortfolio.computeIfAbsent(event.getPortfolioId(), k -> new java.util.ArrayList<>())
                            .add(event);
                }

                // STEP 3: Process each portfolio's batch (maintains strict ordering)
                for (var entry : eventsByPortfolio.entrySet()) {
                    java.util.UUID portfolioId = entry.getKey();
                    List<OutboxEvent> portfolioBatch = entry.getValue();

                    // Process this portfolio's events (prefix-safe, failure-classified)
                    BatchProcessingResult result = processor.processBatch(portfolioBatch);

                    // STEP 4: Handle results within transaction
                    transactionTemplate.execute(status -> {
                        // 4a. Mark successful prefix as SENT (SINGLE DB UPDATE per portfolio)
                        if (!result.getSuccessfulIds().isEmpty()) {
                            outboxRepo.markBatchAsSent(result.getSuccessfulIds());
                            log.info("Portfolio {}: Marked {} events as SENT", portfolioId,
                                    result.getSuccessfulIds().size());
                        }

                        // 4b. Handle poison pill (if any)
                        if (result.hasPoisonPill()) {
                            PoisonPillException ppe = result.getPoisonPill();
                            OutboxEvent poisonEvent = findEventById(portfolioBatch, ppe.getEventId());
                            if (poisonEvent != null) {
                                moveToDlq(poisonEvent, ppe.getMessage());
                                log.warn("Portfolio {}: Routed poison pill {} to DLQ", portfolioId, ppe.getEventId());
                            }
                        }

                        return null;
                    });

                    // STEP 5: Backoff strategy for system failures
                    if (result.hasSystemFailure()) {
                        // Exponential backoff
                        currentBackoff = currentBackoff == 0 ? systemFailureBackoffMs
                                : Math.min(currentBackoff * 2, maxBackoffMs);
                        log.error("Portfolio {}: System failure detected. Backoff={}ms. Will retry on next iteration.",
                                portfolioId, currentBackoff);
                        break; // Stop processing other portfolios, apply backoff
                    } else {
                        // Success or poison pill (not a system issue)
                        currentBackoff = 0; // Reset backoff
                    }
                }

                // Feedback for adaptive sizing (only if no system failure)
                if (currentBackoff == 0) {
                    long duration = System.currentTimeMillis() - startTime;
                    batchSizer.adjust(duration, batch.size());
                }

            } catch (Exception e) {
                log.error("Unexpected error in dispatch loop", e);
                // Defensive: backoff and continue
                currentBackoff = systemFailureBackoffMs;
                sleep(currentBackoff);
            }
        }
    }

    /**
     * Moves a poison pill event to the dead letter queue and removes it from the outbox.
     * Must be called within a transaction.
     */
    private void moveToDlq(OutboxEvent event, String errorMsg) {
        DlqEntry dlqEntry = new DlqEntry(event.getPayload(), "Poison Pill: " + errorMsg);
        dlqRepo.save(dlqEntry);
        outboxRepo.delete(event);
    }

    /**
     * Finds an event by ID within a batch of events.
     */
    private OutboxEvent findEventById(List<OutboxEvent> batch, Long eventId) {
        return batch.stream()
                .filter(e -> e.getId().equals(eventId))
                .findFirst()
                .orElse(null);
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            running = false;
        }
    }
}
