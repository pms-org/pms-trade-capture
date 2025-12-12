package com.pms.pms_trade_capture.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "dlq_entry")
@Data
@NoArgsConstructor
public class DlqEntry {
    //Note: allocationSize = 50 matches hibernate.jdbc.batch_size.
    //This is critical for batch inserts to work efficiently without fetching a sequence value for every single row.
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "dlq_seq")
    @SequenceGenerator(name = "dlq_seq", sequenceName = "dlq_entry_seq", allocationSize = 1)
    private Long id;

    @Column(name = "failed_at", nullable = false)
    private LocalDateTime failedAt = LocalDateTime.now();

    @Lob // Large Object for potentially massive invalid payloads
    @Column(name = "raw_message", nullable = false)
    private byte[] rawMessage;

    @Column(name = "error_detail")
    private String errorDetail;

    public DlqEntry(byte[] rawMessage, String errorDetail) {
        this.rawMessage = rawMessage;
        this.errorDetail = errorDetail;
    }
}
