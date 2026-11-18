package com.lq.lan.slave;

import com.lq.lan.LanConfig;
import com.lq.lan.LanProtocol;
import com.lq.lan.net.DeviceIdentity;

import org.java_websocket.WebSocket;
import org.java_websocket.drafts.Draft;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.protocols.IProtocol;
import org.java_websocket.protocols.Protocol;
import org.java_websocket.server.WebSocketServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.Collections;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** 用一个最小的 Java-WebSocket 服务端验证 OkHttp 版 SlaveClient 的生命周期回调 */
public class SlaveClientIntegrationTest {

    private final BlockingQueue<String> serverEvents = new LinkedBlockingQueue<>();
    private final BlockingQueue<String> clientEvents = new LinkedBlockingQueue<>();
    private LanConfig config;
    private WebSocketServer server;
    private volatile WebSocket serverSide;

    @Before
    public void setUp() throws Exception {
        int port;
        try (ServerSocket s = new ServerSocket(0)) {
            port = s.getLocalPort();
        }
        config = LanConfig.builder().wsPort(port).build();
        server = new WebSocketServer(new InetSocketAddress(port), Collections.<Draft>singletonList(
                new Draft_6455(Collections.emptyList(),
                        Collections.<IProtocol>singletonList(new Protocol(config.wsProtocol))))) {
            @Override public void onOpen(WebSocket c, ClientHandshake h) {
                serverSide = c;
                serverEvents.add("open:" + LanProtocol.decode(h.getFieldValue(LanProtocol.HEADER_DEVICE_NAME)));
            }
            @Override public void onClose(WebSocket c, int code, String r, boolean remote) { serverEvents.add("close"); }
            @Override public void onMessage(WebSocket c, String m) { serverEvents.add("text:" + m); }
            @Override public void onMessage(WebSocket c, java.nio.ByteBuffer b) { serverEvents.add("bytes:" + b.remaining()); }
            @Override public void onError(WebSocket c, Exception e) { }
            @Override public void onStart() { serverEvents.add("start"); }
        };
        server.setReuseAddr(true);
        server.start();
        assertEquals("start", serverEvents.poll(3, TimeUnit.SECONDS));
    }

    @After
    public void tearDown() throws Exception {
        server.stop(500);
    }

    private SlaveClient newClient() {
        return new SlaveClient(config, DeviceIdentity.of("test-id", "学生 Pad"), new SlaveClient.Listener() {
            @Override public void onConnected() { clientEvents.add("connected"); }
            @Override public void onConnectFailed(boolean rejected, Exception e) { clientEvents.add(rejected ? "rejected" : "failed"); }
            @Override public void onClosed(int code, Exception e) { clientEvents.add("closed:" + code); }
            @Override public void onText(String text) { clientEvents.add("text:" + text); }
        });
    }

    @Test
    public void connectExchangeAndDetectServerClose() throws Exception {
        SlaveClient client = newClient();
        client.connect("127.0.0.1", config.wsPort);
        assertEquals("connected", clientEvents.poll(3, TimeUnit.SECONDS));
        assertEquals("open:学生 Pad", serverEvents.poll(3, TimeUnit.SECONDS));
        assertTrue(client.isConnected());

        assertTrue(client.send("举手"));
        assertEquals("text:举手", serverEvents.poll(3, TimeUnit.SECONDS));

        serverSide.send("请回答");
        assertEquals("text:请回答", clientEvents.poll(3, TimeUnit.SECONDS));

        assertTrue(client.sendBytes(new byte[]{1, 2, 3}));
        assertEquals("bytes:3", serverEvents.poll(3, TimeUnit.SECONDS));

        serverSide.close(1000, "bye");
        assertEquals("closed:1000", clientEvents.poll(3, TimeUnit.SECONDS));
    }

    @Test
    public void connectFailureReported() throws Exception {
        SlaveClient client = newClient();
        client.connect("127.0.0.1", 1); // 无服务的端口
        assertEquals("failed", clientEvents.poll(6, TimeUnit.SECONDS));
    }

    /** 主动 close 后不应再回调 onClosed（否则状态机会误触发重连） */
    @Test
    public void manualCloseIsSilent() throws Exception {
        SlaveClient client = newClient();
        client.connect("127.0.0.1", config.wsPort);
        assertEquals("connected", clientEvents.poll(3, TimeUnit.SECONDS));
        client.close();
        assertEquals("open:学生 Pad", serverEvents.poll(3, TimeUnit.SECONDS));
        assertEquals("close", serverEvents.poll(5, TimeUnit.SECONDS));
        assertEquals(null, clientEvents.poll(500, TimeUnit.MILLISECONDS));
    }
}
