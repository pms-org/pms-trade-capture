package com.pms.pms_trade_capture.exception;

/**
 * Exception indicating a permanently corrupted event that should be sent to DLQ.
 * Used for data corruption issues like deserialization errors or invalid formats.
 */
public class PoisonPillException extends Exception {
    private final Long eventId;
    
    public PoisonPillException(Long eventId, String message, Throwable cause) {
        super(message, cause);
        this.eventId = eventId;
    }
    
    public Long getEventId() {
        return eventId;
    }
}
