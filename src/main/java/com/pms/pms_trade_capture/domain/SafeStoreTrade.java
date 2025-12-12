package com.pms.pms_trade_capture.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "safe_store_trade")
@Data
@NoArgsConstructor
public class SafeStoreTrade {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime insertedAt;

    @Column(name = "portfolio_id", nullable = false)
    private UUID portfolioId;

    // Business Key (Ensures Idempotency)
    @Column(name = "trade_id", nullable = false, unique = true)
    private UUID tradeId;

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false)
    private String side;

    @Column(name = "price_per_stock", nullable = false)
    private double pricePerStock;

    @Column(nullable = false)
    private long quantity;

    // Business timestamp (from the exchange/simulator)
    @Column(name = "event_timestamp", nullable = false)
    private LocalDateTime eventTimestamp;

    // Custom constructor for mapper usage
    public SafeStoreTrade(UUID portfolioId, UUID tradeId, String symbol, String side,
                          double pricePerStock, long quantity, LocalDateTime eventTimestamp) {
        this.portfolioId = portfolioId;
        this.tradeId = tradeId;
        this.symbol = symbol;
        this.side = side;
        this.pricePerStock = pricePerStock;
        this.quantity = quantity;
        this.eventTimestamp = eventTimestamp;
    }

}
