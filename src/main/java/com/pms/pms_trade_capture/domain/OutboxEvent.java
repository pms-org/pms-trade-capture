package com.pms.pms_trade_capture.domain;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "outbox_event")
@Data
@NoArgsConstructor
public class OutboxEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "safe_store_seq")
    @SequenceGenerator(name = "safe_store_seq", sequenceName = "safe_store_trade_seq", allocationSize = 50)
    private Long id;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // Partition Key for Ordered Polling
    @Column(name = "portfolio_id", nullable = false)
    private UUID portfolioId;

    @Column(name = "trade_id", nullable = false)
    private UUID tradeId;

    // Raw Protobuf bytes (Source of Truth for downstream Kafka)
    @Column(name = "payload", nullable = false)
    private byte[] payload;

    @Column(name = "status", nullable = false)
    private String status = "PENDING";

    @Column(name = "attempts", nullable = false)
    private int attempts = 0;

    /**
     * Constructor for creating new outbox events with proper initialization.
     */
    public OutboxEvent(UUID portfolioId, UUID tradeId, byte[] payload) {
        this.portfolioId = portfolioId;
        this.tradeId = tradeId;
        this.payload = payload;
        this.status = "PENDING";
        this.createdAt = LocalDateTime.now();
    }
}
