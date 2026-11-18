package com.lq.lan.master;

import android.util.Log;

import com.lq.lan.LanConfig;
import com.lq.lan.LanProtocol;
import com.lq.lan.util.MainThread;

import org.java_websocket.WebSocket;
import org.java_websocket.drafts.Draft;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.exceptions.InvalidDataException;
import org.java_websocket.exceptions.WebsocketNotConnectedException;
import org.java_websocket.framing.CloseFrame;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.ServerHandshakeBuilder;
import org.java_websocket.protocols.IProtocol;
import org.java_websocket.protocols.Protocol;
import org.java_websocket.server.WebSocketServer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * 主机 WebSocket 服务端（基于 Java-WebSocket），维护 设备ID -> 连接 的映射。所有 Listener 回调都在主线程。
 * <ul>
 *     <li>握手阶段校验子协议与分组，其它分组/已满时直接拒绝握手</li>
 *     <li>同一设备重连时用新连接顶替旧连接（旧连接多半是对端异常断开后的残留）</li>
 *     <li>服务端定期 ping，超过 heartbeatTimeout 无 pong 的连接由框架自动断开</li>
 *     <li>单帧上限 {@link #MAX_FRAME_BYTES}，防止异常客户端撑爆内存</li>
 * </ul>
 */
final class MasterServer {

    interface Listener {
        void onServerError(Exception e);

        void onClientConnected(ClientInfo client);

        void onClientDisconnected(ClientInfo client);

        void onText(ClientInfo client, String text);

        void onFile(ClientInfo client, File file);
    }

    private static final String TAG = "LanMasterServer";
    static final int MAX_FRAME_BYTES = 16 * 1024 * 1024;

    private final LanConfig config;
    private final File receiveDir;
    private final Listener listener;
    // 声明为 ConcurrentHashMap 而非 Map：remove(k, v) 在 Map 接口上需要 API 24
    private final ConcurrentHashMap<String, WebSocket> clients = new ConcurrentHashMap<>();
    /** 启停（stop 会阻塞等待线程退出）与文件落盘都放在这个串行线程里，保证先停后启的顺序 */
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "lan-master-io");
        t.setDaemon(true);
        return t;
    });
    private volatile Server server;

    MasterServer(LanConfig config, File receiveDir, Listener listener) {
        this.config = config;
        this.receiveDir = receiveDir;
        this.listener = listener;
    }

    void start() {
        if (server != null) return;
        // WebSocketServer 实例不可重复启动，每次新建
        final Server s = new Server();
        server = s;
        io.execute(s::start);
    }

    void stop() {
        final Server s = server;
        if (s == null) return;
        server = null;
        clients.clear();
        io.execute(() -> {
            try {
                s.stop(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    void release() {
        stop();
        io.shutdown();
    }

    int clientCount() {
        return clients.size();
    }

    List<ClientInfo> clients() {
        List<ClientInfo> list = new ArrayList<>(clients.size());
        for (WebSocket ws : clients.values()) list.add(ws.<ClientInfo>getAttachment());
        return list;
    }

    /** 群发，返回实际发送的连接数 */
    int broadcast(String text) {
        int n = 0;
        for (WebSocket ws : clients.values()) {
            if (safeSend(ws, text)) n++;
        }
        return n;
    }

    boolean sendTo(String clientId, String text) {
        WebSocket ws = clients.get(clientId);
        return ws != null && safeSend(ws, text);
    }

    /** isOpen 与 send 之间连接随时可能断开，send 会抛异常，不能让它冒泡到主线程 */
    private static boolean safeSend(WebSocket ws, String text) {
        if (!ws.isOpen()) return false;
        try {
            ws.send(text);
            return true;
        } catch (WebsocketNotConnectedException e) {
            return false;
        }
    }

    private void saveFile(ClientInfo client, byte[] payload) {
        if (!receiveDir.exists() && !receiveDir.mkdirs()) {
            Log.e(TAG, "cannot create " + receiveDir);
            return;
        }
        File file = new File(receiveDir, client.ip.replace('.', '_') + "_" + System.currentTimeMillis() + ".bin");
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(payload);
        } catch (IOException e) {
            Log.e(TAG, "save file failed", e);
            return;
        }
        MainThread.post(() -> listener.onFile(client, file));
    }

    private static ClientInfo identify(WebSocket ws, ClientHandshake handshake) {
        InetSocketAddress a = ws.getRemoteSocketAddress();
        String ip = a != null && a.getAddress() != null ? a.getAddress().getHostAddress() : "unknown";
        String id = handshake.getFieldValue(LanProtocol.HEADER_DEVICE_ID);
        String name = LanProtocol.decode(handshake.getFieldValue(LanProtocol.HEADER_DEVICE_NAME));
        return new ClientInfo(id.isEmpty() ? ip : id, ip, name);
    }

    // ---------------- 以下回调运行在 Java-WebSocket 的工作线程 ----------------

    private final class Server extends WebSocketServer {

        Server() {
            super(new InetSocketAddress(config.wsPort), Collections.<Draft>singletonList(
                    new Draft_6455(Collections.emptyList(),
                            Collections.<IProtocol>singletonList(new Protocol(config.wsProtocol)),
                            MAX_FRAME_BYTES)));
            setReuseAddr(true);
            setTcpNoDelay(true);
            setConnectionLostTimeout((int) Math.max(1, config.heartbeatTimeoutMs / 1000));
        }

        @Override
        public void onStart() {
            Log.i(TAG, "listening on ws " + config.wsPort);
        }

        /** 握手阶段拒绝：对端会收到 HTTP 错误而不是「连上又被断开」，不会误以为连接成功 */
        @Override
        public ServerHandshakeBuilder onWebsocketHandshakeReceivedAsServer(
                WebSocket conn, Draft draft, ClientHandshake request) throws InvalidDataException {
            ServerHandshakeBuilder builder = super.onWebsocketHandshakeReceivedAsServer(conn, draft, request);
            String team = request.getFieldValue(LanProtocol.HEADER_TEAM);
            String task = request.getFieldValue(LanProtocol.HEADER_TASK);
            // 1.0 从机不带分组头，放行（它在 UDP 阶段已经校验过分组）
            if (!team.isEmpty() && !(team.equals(config.teamId) && task.equals(config.taskId))) {
                throw new InvalidDataException(CloseFrame.POLICY_VALIDATION, "group mismatch");
            }
            String id = identify(conn, request).id;
            if (!clients.containsKey(id) && clients.size() >= config.maxSlaves) {
                throw new InvalidDataException(CloseFrame.TRY_AGAIN_LATER, "server full");
            }
            return builder;
        }

        @Override
        public void onOpen(WebSocket ws, ClientHandshake handshake) {
            if (server != this) {
                ws.close(CloseFrame.GOING_AWAY);
                return;
            }
            final ClientInfo client = identify(ws, handshake);
            // onClose 时对端地址可能已不可读，身份信息挂在连接上
            ws.setAttachment(client);
            WebSocket old;
            // 多个 worker 线程可能并发 onOpen，「检查上限 + 登记」需要原子
            synchronized (clients) {
                old = clients.get(client.id);
                if (old == null && clients.size() >= config.maxSlaves) {
                    Log.w(TAG, "reject " + client + ": reach max slaves " + config.maxSlaves);
                    ws.close(LanProtocol.CLOSE_SERVER_FULL, "server full");
                    return;
                }
                clients.put(client.id, ws);
            }
            Log.i(TAG, "client connected " + client + ", total=" + clients.size());
            if (old != null) {
                Log.i(TAG, "replace stale connection of " + client);
                old.close(LanProtocol.CLOSE_REPLACED, "replaced"); // 它的 onClose 里 remove(id, old) 会失败，不会误删新连接
            } else {
                MainThread.post(() -> listener.onClientConnected(client));
            }
        }

        @Override
        public void onClose(WebSocket ws, int code, String reason, boolean remote) {
            ClientInfo client = ws.getAttachment();
            if (client == null) return; // 握手未完成就断开
            Log.d(TAG, client + " closed code=" + code + " reason=" + reason);
            if (clients.remove(client.id, ws)) {
                MainThread.post(() -> listener.onClientDisconnected(client));
            }
        }

        @Override
        public void onMessage(WebSocket ws, String message) {
            ClientInfo client = ws.getAttachment();
            if (client != null) MainThread.post(() -> listener.onText(client, message));
        }

        @Override
        public void onMessage(WebSocket ws, ByteBuffer message) {
            ClientInfo client = ws.getAttachment();
            if (client == null) return;
            final byte[] payload = new byte[message.remaining()];
            message.get(payload);
            try {
                io.execute(() -> saveFile(client, payload));
            } catch (RejectedExecutionException ignore) {
                // 已 release
            }
        }

        @Override
        public void onError(WebSocket ws, Exception ex) {
            if (ws != null) {
                // 单个连接的异常，随后会走 onClose
                Log.d(TAG, "connection error: " + ex.getMessage());
                return;
            }
            // ws == null 表示服务端本身出错，例如端口被占用
            Log.e(TAG, "server error", ex);
            if (server == this) {
                MainThread.post(() -> listener.onServerError(ex));
            }
        }
    }
}
