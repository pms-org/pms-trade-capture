package com.pms.pms_trade_capture.outbox;

import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AdaptiveBatchSizer {
    private static final Logger log = LoggerFactory.getLogger(AdaptiveBatchSizer.class);

    @Value("${app.outbox.target-latency-ms:200}")
    private long targetLatencyMs;

    @Value("${app.outbox.min-batch:10}")
    private int minBatchSize;

    @Value("${app.outbox.max-batch:2000}")
    private int maxBatchSize;

    private final AtomicInteger currentBatchSize = new AtomicInteger(10);

    /**
     * Adjusts batch size based on processing performance.
     * Increases size when processing is fast, decreases when slow, resets when queue is empty.
     *
     * @param timeTakenMs time spent processing the batch
     * @param recordsProcessed number of records in the batch
     */
    public void adjust(Long timeTakenMs, int recordsProcessed){
        int current = currentBatchSize.get();
        int next = current;

        // Reset to minimum when queue is draining (got fewer records than requested)
        if (recordsProcessed < current) {
            next = minBatchSize;
        }
        // Grow or shrink based on performance when at full capacity
        else {
            if (timeTakenMs < targetLatencyMs) {
                // Processing is fast - increase batch size
                next = (int) (current * 1.2);
                next = Math.min(next, maxBatchSize);
            } else {
                // Processing is slow - decrease batch size
                next = (int) (current * 0.7);
                next = Math.max(next, minBatchSize);
            }
        }

        // Update and log only when size changes significantly
        if (next != current) {
            currentBatchSize.set(next);
            log.debug("Batch size adjusted: {}ms latency, {} records, size: {} -> {}",
                    timeTakenMs, recordsProcessed, current, next);
        }
    }

    public int getCurrentSize() {
        return currentBatchSize.get();
    }

    public void reset() {
        currentBatchSize.set(minBatchSize);
    }
}
