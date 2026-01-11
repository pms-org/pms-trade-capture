package com.pms.pms_trade_capture.controller;

import java.util.HexFormat;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pms.pms_trade_capture.domain.PendingStreamMessage;
import com.pms.pms_trade_capture.service.BatchingIngestService;

@RestController
@RequestMapping("/admin/replay")
public class ReplayController {

    private final BatchingIngestService ingestService;

    public ReplayController(BatchingIngestService ingestService) {
        this.ingestService = ingestService;
    }

    @PostMapping("/hex")
    public ResponseEntity<String> replayHexTrade(@RequestBody String hexData) {
        try {
            // Convert hex string to bytes
            byte[] rawBytes = HexFormat.of().parseHex(hexData);

            // Create message for replay (dummy offset, no RabbitMQ context needed)
            PendingStreamMessage msg = new PendingStreamMessage(null, rawBytes, -1, null);

            // Inject into processing buffer
            ingestService.addMessage(msg);

            return ResponseEntity.ok("Replay injected into buffer.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Invalid Hex");
        }
    }
}
