package com.lq.lan.discovery;

import com.lq.lan.LanConfig;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DiscoveryMessageTest {

    private final LanConfig config = LanConfig.defaults();

    @Test
    public void masterRoundTrip() {
        DiscoveryMessage m = DiscoveryMessage.parse(DiscoveryMessage.master(config, "Pad-教师").toJson());
        assertNotNull(m);
        assertEquals(DiscoveryMessage.Role.MASTER, m.role);
        assertEquals("Pad-教师", m.deviceName);
        assertEquals(config.wsPort, m.wsPort);
        assertTrue(m.sameGroup(config));
    }

    @Test
    public void parsesLegacyV1SlavePacket() {
        String v1 = "{\"type\":\"slave\",\"teamId\":\"Speakin\",\"taskId\":\"Test\",\"deviceName\":\"MI 8\"}";
        DiscoveryMessage m = DiscoveryMessage.parse(v1);
        assertNotNull(m);
        assertEquals(DiscoveryMessage.Role.SLAVE, m.role);
        assertEquals(0, m.wsPort);
        assertTrue(m.sameGroup(config));
    }

    /** 回归旧 bug：旧版主机用 task.equals(teamId) 判断，team 与 task 不同时永远不匹配 */
    @Test
    public void groupMatchUsesBothFields() {
        DiscoveryMessage other = new DiscoveryMessage(DiscoveryMessage.Role.SLAVE, "Speakin", "Other", "", 0);
        assertFalse(other.sameGroup(config));
        DiscoveryMessage swapped = new DiscoveryMessage(DiscoveryMessage.Role.SLAVE, "Test", "Speakin", "", 0);
        assertFalse(swapped.sameGroup(config));
    }

    @Test
    public void rejectsGarbage() {
        assertNull(DiscoveryMessage.parse(null));
        assertNull(DiscoveryMessage.parse(""));
        assertNull(DiscoveryMessage.parse("hello"));
        assertNull(DiscoveryMessage.parse("{\"type\":\"router\",\"teamId\":\"a\",\"taskId\":\"b\"}"));
        assertNull(DiscoveryMessage.parse("{\"type\":\"master\",\"teamId\":\"a\"}"));
    }
}
