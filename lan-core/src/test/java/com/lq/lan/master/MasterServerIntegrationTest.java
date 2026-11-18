package com.lq.lan.master;

import com.lq.lan.LanConfig;
import com.lq.lan.LanProtocol;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.net.ServerSocket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * 在 JVM 上起真实的 Java-WebSocket 服务端，用 OkHttp 作为客户端连接，验证两端互通。
 * 单测环境下 Android Handler 是空桩，MainThread.post 会同步执行，回调直接在网络线程里触发。
 */
public class MasterServerIntegrationTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private final BlockingQueue<String> events = new LinkedBlockingQueue<>();
    private final OkHttpClient http = new OkHttpClient();
    private LanConfig config;
    private MasterServer server;

    @Before
    public void setUp() throws Exception {
        start(LanConfig.builder().maxSlaves(2));
    }

    private void start(LanConfig.Builder b) throws Exception {
        int port;
        try (ServerSocket s = new ServerSocket(0)) {
            port = s.getLocalPort();
        }
        config = b.wsPort(port).build();
        server = new MasterServer(config, tmp.newFolder(), new MasterServer.Listener() {
            @Override public void onServerError(Exception e) { events.add("error:" + e); }
            @Override public void onClientConnected(ClientInfo c) { events.add("connected:" + c.label()); }
            @Override public void onClientDisconnected(ClientInfo c) { events.add("disconnected:" + c.label()); }
            @Override public void onText(ClientInfo c, String text) { events.add("text:" + text); }
            @Override public void onFile(ClientInfo c, File file) { events.add("file:" + file.length()); }
        });
        server.start();
        Thread.sleep(500);
    }

    @After
    public void tearDown() {
        server.release();
    }

    private WebSocket connect(BlockingQueue<String> clientEvents, String protocol, String... headers) {
        Request.Builder rb = new Request.Builder().url("ws://127.0.0.1:" + config.wsPort);
        if (protocol != null) rb.header("Sec-WebSocket-Protocol", protocol);
        for (int i = 0; i < headers.length; i += 2) rb.header(headers[i], headers[i + 1]);
        return http.newWebSocket(rb.build(), new WebSocketListener() {
            @Override public void onOpen(WebSocket ws, Response r) { clientEvents.add("open"); }
            @Override public void onMessage(WebSocket ws, String text) { clientEvents.add("text:" + text); }
            @Override public void onClosing(WebSocket ws, int code, String reason) { clientEvents.add("closing:" + code); }
            @Override public void onFailure(WebSocket ws, Throwable t, Response r) {
                clientEvents.add(r != null ? "rejected" : "failure");
            }
        });
    }

    private WebSocket connectAs(BlockingQueue<String> q, String id, String name) {
        return connect(q, config.wsProtocol,
                LanProtocol.HEADER_DEVICE_ID, id,
                LanProtocol.HEADER_DEVICE_NAME, LanProtocol.encode(name),
                LanProtocol.HEADER_TEAM, config.teamId,
                LanProtocol.HEADER_TASK, config.taskId);
    }

    /** 1.0 从机：不带任何自定义头，按 IP 识别 */
    @Test
    public void legacyClientTextAndBinaryBothWays() throws Exception {
        BlockingQueue<String> client = new LinkedBlockingQueue<>();
        WebSocket ws = connect(client, config.wsProtocol);
        assertEquals("open", client.poll(3, TimeUnit.SECONDS));
        assertEquals("connected:127.0.0.1", events.poll(3, TimeUnit.SECONDS));

        ws.send("你好，老师");
        assertEquals("text:你好，老师", events.poll(3, TimeUnit.SECONDS));

        assertEquals(1, server.broadcast("上课了"));
        assertEquals("text:上课了", client.poll(3, TimeUnit.SECONDS));
        assertTrue(server.sendTo("127.0.0.1", "单发"));
        assertEquals("text:单发", client.poll(3, TimeUnit.SECONDS));

        ws.send(ByteString.of(new byte[1024]));
        assertEquals("file:1024", events.poll(3, TimeUnit.SECONDS));

        ws.close(1000, null);
        assertEquals("disconnected:127.0.0.1", events.poll(3, TimeUnit.SECONDS));
        assertEquals(0, server.clientCount());
    }

    @Test
    public void identifiesByDeviceIdWithChineseName() throws Exception {
        BlockingQueue<String> a = new LinkedBlockingQueue<>();
        BlockingQueue<String> b = new LinkedBlockingQueue<>();
        connectAs(a, "id-a", "学生平板A");
        connectAs(b, "id-b", "Pad B");
        assertEquals("open", a.poll(3, TimeUnit.SECONDS));
        assertEquals("open", b.poll(3, TimeUnit.SECONDS));
        String e1 = events.poll(3, TimeUnit.SECONDS);
        String e2 = events.poll(3, TimeUnit.SECONDS);
        assertTrue(e1 + e2, (e1 + e2).contains("学生平板A (127.0.0.1)"));
        assertEquals(2, server.clientCount()); // 同 IP、不同设备 ID 互不顶替

        assertTrue(server.sendTo("id-b", "只发给 B"));
        assertEquals("text:只发给 B", b.poll(3, TimeUnit.SECONDS));
        assertNull(a.poll(300, TimeUnit.MILLISECONDS));
    }

    @Test
    public void rejectsWrongSubProtocolAndOtherGroup() throws Exception {
        BlockingQueue<String> q = new LinkedBlockingQueue<>();
        connect(q, "other");
        assertEquals("rejected", q.poll(3, TimeUnit.SECONDS));
        connect(q, null);
        assertEquals("rejected", q.poll(3, TimeUnit.SECONDS));
        connect(q, config.wsProtocol, LanProtocol.HEADER_TEAM, "OtherTeam", LanProtocol.HEADER_TASK, config.taskId);
        assertEquals("rejected", q.poll(3, TimeUnit.SECONDS));
        assertEquals(0, server.clientCount());
    }

    @Test
    public void rejectsHandshakeWhenFull() throws Exception {
        BlockingQueue<String> q = new LinkedBlockingQueue<>();
        connectAs(q, "1", "a");
        connectAs(q, "2", "b");
        assertEquals("open", q.poll(3, TimeUnit.SECONDS));
        assertEquals("open", q.poll(3, TimeUnit.SECONDS));
        BlockingQueue<String> third = new LinkedBlockingQueue<>();
        connectAs(third, "3", "c");
        assertEquals("rejected", third.poll(3, TimeUnit.SECONDS));
        assertEquals(2, server.clientCount());
    }

    /** 同一设备重连：新连接顶替旧连接，不产生额外的断开事件 */
    @Test
    public void sameDeviceReconnectReplacesOld() throws Exception {
        BlockingQueue<String> first = new LinkedBlockingQueue<>();
        connectAs(first, "same", "x");
        assertEquals("open", first.poll(3, TimeUnit.SECONDS));
        assertEquals("connected:x (127.0.0.1)", events.poll(3, TimeUnit.SECONDS));

        BlockingQueue<String> second = new LinkedBlockingQueue<>();
        WebSocket ws2 = connectAs(second, "same", "x");
        assertEquals("open", second.poll(3, TimeUnit.SECONDS));
        assertEquals("closing:" + LanProtocol.CLOSE_REPLACED, first.poll(3, TimeUnit.SECONDS));
        assertNull(events.poll(300, TimeUnit.MILLISECONDS));
        assertEquals(1, server.clientCount());

        ws2.send("from second");
        assertEquals("text:from second", events.poll(3, TimeUnit.SECONDS));
    }
}
