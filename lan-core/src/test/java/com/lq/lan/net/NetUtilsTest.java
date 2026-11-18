package com.lq.lan.net;

import org.junit.Test;

import java.net.Inet4Address;
import java.net.InetAddress;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class NetUtilsTest {

    private static Inet4Address v4(String s) throws Exception {
        return (Inet4Address) InetAddress.getByName(s);
    }

    @Test
    public void directedBroadcast() throws Exception {
        assertEquals("192.168.1.255", NetUtils.directedBroadcast(v4("192.168.1.23"), 24).getHostAddress());
        assertEquals("192.168.43.255", NetUtils.directedBroadcast(v4("192.168.43.1"), 24).getHostAddress());
        assertEquals("10.0.15.255", NetUtils.directedBroadcast(v4("10.0.8.7"), 20).getHostAddress());
        assertEquals("172.16.255.255", NetUtils.directedBroadcast(v4("172.16.3.4"), 16).getHostAddress());
        assertNull(NetUtils.directedBroadcast(v4("10.0.0.1"), 32));
        assertNull(NetUtils.directedBroadcast(v4("10.0.0.1"), 0));
    }

    @Test
    public void privateRanges() {
        assertTrue(NetUtils.isPrivate("10.1.2.3"));
        assertTrue(NetUtils.isPrivate("192.168.0.1"));
        assertTrue(NetUtils.isPrivate("172.20.0.1"));
        assertFalse(NetUtils.isPrivate("172.32.0.1"));
        assertFalse(NetUtils.isPrivate("8.8.8.8"));
    }

    @Test
    public void wifiPreferredOverCellular() {
        assertTrue(NetUtils.rankInterface("wlan0") < NetUtils.rankInterface("rmnet_data0"));
        assertTrue(NetUtils.rankInterface("ap0") < NetUtils.rankInterface("ccmni1"));
        assertTrue(NetUtils.rankInterface("eth0") < NetUtils.rankInterface("dummy0"));
    }
}
