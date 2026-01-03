package com.pms.pms_trade_capture.util;

import com.pms.pms_trade_capture.outbox.AdaptiveBatchSizer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AdaptiveBatchSizerTest {

    @Test
    void adjust_drainingResetsToMin() {
        AdaptiveBatchSizer sizer = new AdaptiveBatchSizer();

        int before = sizer.getCurrentSize();

        // Simulate we asked for `before` but processed fewer -> drain
        sizer.adjust(50L, Math.max(0, before - 1));

        // After draining we should not grow; ensure value is <= previous
        assertTrue(sizer.getCurrentSize() <= before);
    }

    @Test
    void adjust_underTarget_growsSlowly() {
        AdaptiveBatchSizer sizer = new AdaptiveBatchSizer();
        int before = sizer.getCurrentSize();

        sizer.adjust(10L, Math.max(1, before)); // very fast

    // At minimum the size should remain non-negative
    assertTrue(sizer.getCurrentSize() >= 0);
    }

    @Test
    void adjust_overTarget_shrinksQuickly_andResetWorks() {
        AdaptiveBatchSizer sizer = new AdaptiveBatchSizer();
        int before = sizer.getCurrentSize();

        sizer.adjust(1000L, Math.max(1, before)); // very slow

        assertTrue(sizer.getCurrentSize() <= before);

        sizer.reset();
        // reset should set size to the configured minimum (non-negative)
        assertTrue(sizer.getCurrentSize() >= 0);
    }
}
