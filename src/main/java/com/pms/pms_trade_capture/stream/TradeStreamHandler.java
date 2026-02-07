package com.pms.pms_trade_capture.stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.google.protobuf.InvalidProtocolBufferException;
import com.pms.pms_trade_capture.domain.PendingStreamMessage;
import com.pms.pms_trade_capture.service.BatchingIngestService;
import com.pms.rttm.client.clients.RttmClient;
import com.pms.rttm.client.dto.TradeEventPayload;
import com.pms.rttm.client.enums.EventStage;
import com.pms.rttm.client.enums.EventType;
import com.pms.trade_capture.proto.TradeEventProto;
import com.rabbitmq.stream.MessageHandler;

@Component
public class TradeStreamHandler implements MessageHandler {
    private static final Logger log = LoggerFactory.getLogger(TradeStreamHandler.class);

    private final TradeStreamParser tradeStreamParser;
    private final BatchingIngestService ingestService;
    private final RttmClient rttmClient;

    @Value("${spring.application.name}")
    private String serviceName;

    public TradeStreamHandler(TradeStreamParser tradeStreamParser, 
                             BatchingIngestService ingestService,
                             RttmClient rttmClient) {
        this.tradeStreamParser = tradeStreamParser;
        this.ingestService = ingestService;
        this.rttmClient = rttmClient;
    }

    @Override
    public void handle(com.rabbitmq.stream.MessageHandler.Context context, com.rabbitmq.stream.Message message) {
        long offset = context.offset();
        byte[] body = message.getBodyAsBinary();

        try {
            // Parse the protobuf message
            TradeEventProto trade = tradeStreamParser.parse(body);

            // Validate required fields
            if (trade.getPortfolioId().isEmpty() || trade.getTradeId().isEmpty()) {
                handleInvalidMessage(body, offset, "Missing required fields: PortfolioID or TradeID", context);
                return;
            }

            // Send RTTM event: Trade RECEIVED from RabbitMQ Stream
            sendTradeReceivedEvent(trade, offset);

            // Route valid message for processing
            ingestService.addMessage(new PendingStreamMessage(trade, body, offset, context));

        } catch (InvalidProtocolBufferException e) {
            // Handle malformed protobuf messages
            log.warn("Received malformed Protobuf at offset {}", offset);
            handleInvalidMessage(body, offset, "Invalid Protobuf: " + e.getMessage(), context);
        } catch (Exception e) {
            // Handle unexpected processing errors
            log.error("Unexpected error handling message at offset {}", offset, e);
            handleInvalidMessage(body, offset, "Processing Error: " + e.getMessage(), context);
        }

    }

    /**
     * Send RTTM event when trade is first received from RabbitMQ Stream
     */
    private void sendTradeReceivedEvent(TradeEventProto trade, long offset) {
        try {
            TradeEventPayload event = TradeEventPayload.builder()
                    .serviceName(serviceName)
                    .tradeId(trade.getTradeId())
                    .eventType(EventType.TRADE_RECEIVED)
                    .eventStage(EventStage.RECEIVED)
                    .eventStatus("RECEIVED")
                    .message("Trade received from RabbitMQ Stream at offset " + offset)
                    .build();

            rttmClient.sendTradeEvent(event);
            log.info("RTTM[TRADE_RECEIVED] tradeId={} offset={} portfolio={}", 
                    trade.getTradeId(), offset, trade.getPortfolioId());
        } catch (Exception ex) {
            log.warn("RTTM[TRADE_RECEIVED] FAILED tradeId={}: {}", trade.getTradeId(), ex.getMessage());
        }
    }

    private void handleInvalidMessage(byte[] body, long offset, String reason, MessageHandler.Context context) {
        // Wrap invalid messages for DLQ processing while ensuring stream continues
        ingestService.addMessage(new PendingStreamMessage(body, offset, reason, context));
    }

}
