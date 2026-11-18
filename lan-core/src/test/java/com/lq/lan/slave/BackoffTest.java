package com.lq.lan.slave;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BackoffTest {

    @Test
    public void exponentialWithCap() {
        // rand01 = 0.5 时抖动系数正好为 1
        assertEquals(2_000, SlaveControlManager.backoffMs(2_000, 1, 0.5));
        assertEquals(4_000, SlaveControlManager.backoffMs(2_000, 2, 0.5));
        assertEquals(8_000, SlaveControlManager.backoffMs(2_000, 3, 0.5));
        assertEquals(SlaveControlManager.MAX_BACKOFF_MS, SlaveControlManager.backoffMs(2_000, 50, 0.5));
    }

    @Test
    public void jitterWithin20Percent() {
        long lo = SlaveControlManager.backoffMs(10_000, 1, 0.0);
        long hi = SlaveControlManager.backoffMs(10_000, 1, 0.999999);
        assertEquals(8_000, lo);
        assertTrue(hi <= 12_000 && hi > 11_900);
    }
}
