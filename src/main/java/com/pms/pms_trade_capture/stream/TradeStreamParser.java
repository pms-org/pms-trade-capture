package com.pms.pms_trade_capture.stream;

import com.google.protobuf.InvalidProtocolBufferException;
import org.springframework.stereotype.Component;
import com.pms.trade_capture.proto.TradeEventProto;

@Component
public class TradeStreamParser {
    /**
     * Attempts to parse the raw bytes into a TradeEventProto.
     * Throws checked exception to force caller handling.
     */
    public TradeEventProto parse(byte[] payload) throws InvalidProtocolBufferException {
        return TradeEventProto.parseFrom(payload);
    }
}
