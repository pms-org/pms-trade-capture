package com.pms.pms_trade_capture.outbox;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.MockitoAnnotations;
import org.springframework.kafka.core.KafkaTemplate;

import com.pms.pms_trade_capture.domain.OutboxEvent;
import com.pms.pms_trade_capture.dto.BatchProcessingResult;
import com.pms.rttm.client.clients.RttmClient;
import com.pms.trade_capture.proto.TradeEventProto;

class OutboxEventProcessorTest {

    @Mock
    private KafkaTemplate<String, TradeEventProto> kafkaTemplate;

    @Mock
    private RttmClient rttmClient;

    private java.util.concurrent.CompletableFuture<Object> future;

    private OutboxEventProcessor processor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        processor = new OutboxEventProcessor(kafkaTemplate, rttmClient);
    }

    @Test
    void processBatch_allEventsSent_returnsSuccessPrefix() throws Exception {
        // Arrange: build a valid proto payload
        TradeEventProto proto = TradeEventProto.newBuilder().setPortfolioId("p").setTradeId("t").build();
        OutboxEvent e1 = new OutboxEvent(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), proto.toByteArray());
        e1.setId(1L);

    future = new java.util.concurrent.CompletableFuture<>();
    future.complete(null);
    when(kafkaTemplate.send(any(), any(), any())).thenAnswer(invocation -> future);

        // Act
        BatchProcessingResult result = processor.processBatch(List.of(e1));

        // Assert
        assertTrue(result.isFullSuccess());
        assertEquals(1, result.getSuccessfulIds().size());
        assertEquals(List.of(1L), result.getSuccessfulIds());
    }

    @Test
    void processBatch_poisonPill_detectedAndReturned() {
        // Arrange: invalid payload that will trigger InvalidProtocolBufferException
        OutboxEvent bad = new OutboxEvent(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), new byte[] {0x01, 0x02});
        bad.setId(42L);

        // Act
        BatchProcessingResult result = processor.processBatch(List.of(bad));

        // Assert: poison pill should be detected and returned, no successful ids
        assertTrue(result.hasPoisonPill());
        assertEquals(0, result.getSuccessfulIds().size());
        assertEquals(42L, result.getPoisonPill().getEventId());
    }

    @Test
    void processBatch_kafkaExecutionException_classifiedAsSystemFailure() throws Exception {
        // Arrange: valid payload
        TradeEventProto proto = TradeEventProto.newBuilder().setPortfolioId("p").setTradeId("t").build();
        OutboxEvent e1 = new OutboxEvent(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), proto.toByteArray());
        e1.setId(7L);

    future = new java.util.concurrent.CompletableFuture<>();
    future.completeExceptionally(new RuntimeException("network"));
    when(kafkaTemplate.send(any(), any(), any())).thenAnswer(invocation -> future);

        // Act
        BatchProcessingResult result = processor.processBatch(List.of(e1));

        // Assert: system failure should be reported and no ids marked as sent
        assertTrue(result.hasSystemFailure());
        assertTrue(result.getSuccessfulIds().isEmpty());
    }
}
