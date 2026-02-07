package com.pms.pms_trade_capture.service.metrics;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.pms.rttm.client.clients.RttmClient;
import com.pms.rttm.client.dto.QueueMetricPayload;

import lombok.extern.slf4j.Slf4j;

/**
 * Service to send queue metrics to RTTM at regular intervals.
 * Monitors the trade-capture service's outgoing Kafka topic (where trades are dispatched).
 */
@Service
@Slf4j
public class QueueMetricsService {

    @Autowired
    private RttmClient rttmClient;

    @Autowired
    private KafkaConsumer<String, String> metricsConsumer;

    @Value("${app.outbox.trade-topic}")
    private String outgoingTradeTopic;

    @Value("${spring.kafka.consumer.group-id}")
    private String consumerGroup;

    @Value("${spring.application.name}")
    private String serviceName;

    /**
     * Send queue metrics every 30 seconds
     */
    @Scheduled(fixedDelayString = "${rttm.client.metrics.interval-ms:30000}")
    public void sendQueueMetrics() {
        try {
            // Send metrics for outgoing trade topic (this service's responsibility)
            // We don't monitor incoming RabbitMQ stream as that's simulation service's job
            sendMetricsForAllPartitions(metricsConsumer, outgoingTradeTopic);

            log.debug("RTTM[METRICS] Queue metrics sent for topic: {}", outgoingTradeTopic);
        } catch (Exception ex) {
            log.warn("RTTM[METRICS] FAILED: {}", ex.getMessage());
        }
    }

    /**
     * Send metrics for all partitions of a topic
     */
    private void sendMetricsForAllPartitions(KafkaConsumer<String, String> consumer, String topicName) {
        try {
            // Discover all partitions for this topic
            List<PartitionInfo> partitionInfos = consumer.partitionsFor(topicName);

            if (partitionInfos == null || partitionInfos.isEmpty()) {
                log.warn("No partitions found for topic: {}", topicName);
                return;
            }

            // Send metric for each partition
            for (PartitionInfo partitionInfo : partitionInfos) {
                sendMetricForTopic(consumer, topicName, partitionInfo.partition());
            }
        } catch (Exception ex) {
            log.warn("Failed to get partitions for topic {}: {}", topicName, ex.getMessage());
        }
    }

    /**
     * Send queue metric for a specific topic partition
     */
    private void sendMetricForTopic(KafkaConsumer<String, String> consumer, String topicName, int partitionId) {
        try {
            TopicPartition topicPartition = new TopicPartition(topicName, partitionId);

            // Get the end offset (latest produced offset)
            Map<TopicPartition, Long> endOffsets = consumer.endOffsets(Collections.singletonList(topicPartition));
            long producedOffset = endOffsets.getOrDefault(topicPartition, 0L);

            // Get the committed offset (consumed offset) for this consumer group
            OffsetAndMetadata committedOffset = consumer
                    .committed(Collections.singleton(topicPartition), Duration.ofSeconds(5)).get(topicPartition);
            long consumedOffset = (committedOffset != null) ? committedOffset.offset() : 0L;

            QueueMetricPayload metric = QueueMetricPayload.builder()
                    .serviceName(serviceName)
                    .topicName(topicName)
                    .partitionId(partitionId)
                    .producedOffset(producedOffset)
                    .consumedOffset(consumedOffset)
                    .consumerGroup(consumerGroup)
                    .build();

            rttmClient.sendQueueMetric(metric);
            
            long lag = producedOffset - consumedOffset;
            log.info("📊 RTTM[QUEUE_METRIC] topic={} partition={} produced={} consumed={} lag={}", 
                    topicName, partitionId, producedOffset, consumedOffset, lag);
        } catch (Exception ex) {
            log.warn("⚠️ RTTM[QUEUE_METRIC] FAILED topic={} partition={}: {}", 
                    topicName, partitionId, ex.getMessage());
        }
    }
}
