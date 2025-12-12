package com.pms.pms_trade_capture.dto;

import java.time.Instant;
import java.util.UUID;


public class TradeEventDto {
    public UUID portfolioId;
    public UUID tradeId;
    public String symbol;
    public String side;
    public double pricePerStock;
    public long quantity;
    public Instant timestamp;

    public TradeEventDto() {}

    public TradeEventDto(UUID portfolioId, UUID tradeId, String symbol, String side, double pricePerStock, long quantity, Instant timestamp) {
        this.portfolioId = portfolioId;
        this.tradeId = tradeId;
        this.symbol = symbol;
        this.side = side;
        this.pricePerStock = pricePerStock;
        this.quantity = quantity;
        this.timestamp = timestamp;
    }
}
