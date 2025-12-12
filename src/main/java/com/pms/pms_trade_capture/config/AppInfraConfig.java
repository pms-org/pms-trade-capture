package com.pms.pms_trade_capture.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@EnableScheduling// Enables @Scheduled for the Poller
@EnableAsync
@Configuration
public class AppInfraConfig {
    @Bean("outboxExecutor")
    public Executor outboxExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("outbox-worker-");
        executor.initialize();
        return executor;
    }

    /**
     * Dedicated Single-Thread Scheduler for the Ingestion Buffer Flush.
     * Defined here so it is managed by Spring container.
     */
    @Bean("ingestScheduler")
    public ScheduledExecutorService ingestScheduler() {
        return Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ingest-flusher");
            t.setDaemon(true);
            return t;
        });
    }
}
