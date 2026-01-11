package com.pms.pms_trade_capture.stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.google.protobuf.InvalidProtocolBufferException;
import com.pms.pms_trade_capture.domain.PendingStreamMessage;
import com.pms.pms_trade_capture.service.BatchingIngestService;
import com.pms.trade_capture.proto.TradeEventProto;
import com.rabbitmq.stream.MessageHandler;

@Component
public class TradeStreamHandler implements MessageHandler {
    private static final Logger log = LoggerFactory.getLogger(TradeStreamHandler.class);

    private final TradeStreamParser tradeStreamParser;
    private final BatchingIngestService ingestService;

    public TradeStreamHandler(TradeStreamParser tradeStreamParser, BatchingIngestService ingestService) {
        this.tradeStreamParser = tradeStreamParser;
        this.ingestService = ingestService;
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

    private void handleInvalidMessage(byte[] body, long offset, String reason, MessageHandler.Context context) {
        // Wrap invalid messages for DLQ processing while ensuring stream continues
        ingestService.addMessage(new PendingStreamMessage(body, offset, reason, context));
    }

}
