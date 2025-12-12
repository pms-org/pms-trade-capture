package com.pms.pms_trade_capture.config;

import com.pms.pms_trade_capture.stream.StreamConsumerManager;
import com.pms.pms_trade_capture.outbox.OutboxDispatcher;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class ComponentHealthIndicator implements HealthIndicator {

    private final StreamConsumerManager consumerManager;
    private final OutboxDispatcher outboxDispatcher;

    public ComponentHealthIndicator(StreamConsumerManager consumerManager,
                                    OutboxDispatcher outboxDispatcher) {
        this.consumerManager = consumerManager;
        this.outboxDispatcher = outboxDispatcher;
    }

    @Override
    public Health health() {
        if (!consumerManager.isRunning() || !outboxDispatcher.isRunning()) {
            return Health.down()
                    .withDetail("StreamConsumer", consumerManager.isRunning() ? "UP" : "DOWN")
                    .withDetail("OutboxDispatcher", outboxDispatcher.isRunning() ? "UP" : "DOWN")
                    .build();
        }
        return Health.up().build();
    }
}