package com.lq.lan.slave;

import android.util.Log;

import com.lq.lan.LanConfig;
import com.lq.lan.LanProtocol;
import com.lq.lan.net.DeviceIdentity;
import com.lq.lan.util.MainThread;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

/**
 * 从机 WebSocket 客户端（基于 OkHttp），代表一次连接的生命周期。所有 Listener 回调在主线程。
 * <p>
 * 心跳交给 OkHttp：每 heartbeatInterval 发一次 ping，下一次 ping 前没收到 pong 即判定连接失效并回调 onFailure，
 * 可以快速感知 Wi-Fi 静默断开。
 */
final class SlaveClient {

    interface Listener {
        void onConnected();

        /** rejected=true 表示主机明确拒绝了握手（已满 / 分组不符），应延长退避 */
        void onConnectFailed(boolean rejected, Exception e);

        /** code 为对端关闭码，异常断开时为 -1 */
        void onClosed(int code, Exception e);

        void onText(String text);
    }

    private static final String TAG = "LanSlaveClient";
    /** OkHttp 发送队列上限为 16MB，超过会直接失败并断开连接，这里留出余量 */
    static final long MAX_FILE_BYTES = 15L * 1024 * 1024;
    private static final int REJECTED = -2;

    /** OkHttpClient 持有连接池和线程池，全局共享一个基础实例，按配置派生 */
    private static final OkHttpClient BASE = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS) // 长连接不设读超时，由 ping 负责保活
            .retryOnConnectionFailure(false)       // 重连策略由状态机统一负责
            .build();

    private final LanConfig config;
    private final DeviceIdentity identity;
    private final Listener listener;
    private final OkHttpClient http;
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "lan-slave-io");
        t.setDaemon(true);
        return t;
    });

    private volatile WebSocket socket;
    private boolean opened;
    /** 主动 close() 后置位，之后 OkHttp 的迟到回调一律忽略 */
    private boolean closed;

    SlaveClient(LanConfig config, DeviceIdentity identity, Listener listener) {
        this.config = config;
        this.identity = identity;
        this.listener = listener;
        this.http = BASE.newBuilder()
                .pingInterval(config.heartbeatIntervalMs, TimeUnit.MILLISECONDS)
                .build();
    }

    /** 只允许调用一次；重连请新建实例，避免旧连接的迟到回调污染新状态 */
    void connect(String ip, int port) {
        String url = "ws://" + ip + ":" + port;
        Log.i(TAG, "connect " + url);
        Request request = new Request.Builder()
                .url(url)
                .header("Sec-WebSocket-Protocol", config.wsProtocol)
                .header(LanProtocol.HEADER_DEVICE_ID, identity.id)
                .header(LanProtocol.HEADER_DEVICE_NAME, LanProtocol.encode(identity.name))
                .header(LanProtocol.HEADER_TEAM, config.teamId)
                .header(LanProtocol.HEADER_TASK, config.taskId)
                .build();
        socket = http.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket ws, Response response) {
                MainThread.post(() -> {
                    if (closed) return;
                    opened = true;
                    listener.onConnected();
                });
            }

            @Override
            public void onMessage(WebSocket ws, String text) {
                MainThread.post(() -> {
                    if (!closed) listener.onText(text);
                });
            }

            @Override
            public void onMessage(WebSocket ws, ByteString bytes) {
                // 从机暂不处理二进制消息
                Log.d(TAG, "ignore binary message, size=" + bytes.size());
            }

            @Override
            public void onClosing(WebSocket ws, int code, String reason) {
                ws.close(1000, null); // 回应对端的关闭帧
            }

            @Override
            public void onClosed(WebSocket ws, int code, String reason) {
                finish(code, new IOException("closed by server: " + code + " " + reason));
            }

            @Override
            public void onFailure(WebSocket ws, Throwable t, Response response) {
                // response 非空说明 TCP 已通但握手被拒（HTTP 非 101）
                finish(response != null ? REJECTED : -1, t instanceof Exception ? (Exception) t : new IOException(t));
            }
        });
    }

    private void finish(int code, Exception e) {
        MainThread.post(() -> {
            if (closed) return;
            closed = true;
            socket = null;
            io.shutdown();
            if (opened) listener.onClosed(code, e);
            else listener.onConnectFailed(code == REJECTED, e);
        });
    }

    boolean isConnected() {
        return socket != null && opened && !closed;
    }

    boolean send(String text) {
        WebSocket ws = socket;
        if (!isConnected() || ws == null || text == null || text.isEmpty()) return false;
        return ws.send(text);
    }

    boolean sendBytes(byte[] data) {
        WebSocket ws = socket;
        if (!isConnected() || ws == null || data == null || data.length == 0 || data.length > MAX_FILE_BYTES) return false;
        return ws.send(ByteString.of(data));
    }

    /** 以单个二进制帧发送文件。整文件进内存，限制 15MB；大文件应改为分片协议 */
    boolean sendFile(final File file) {
        if (!isConnected() || file == null || !file.isFile() || file.length() > MAX_FILE_BYTES) return false;
        if (io.isShutdown()) return false;
        io.execute(() -> {
            try (InputStream in = new FileInputStream(file)) {
                ByteArrayOutputStream out = new ByteArrayOutputStream((int) file.length());
                byte[] buf = new byte[8 * 1024];
                int n;
                while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                WebSocket ws = socket;
                if (ws != null) ws.send(ByteString.of(out.toByteArray()));
            } catch (IOException e) {
                Log.e(TAG, "send file failed", e);
            }
        });
        return true;
    }

    /** 主动关闭，不再回调 onClosed */
    void close() {
        if (closed) return;
        closed = true;
        WebSocket ws = socket;
        socket = null;
        if (ws != null) {
            // 已建立的连接走正常关闭握手；握手中或已失效的直接取消
            if (!opened || !ws.close(1000, "bye")) ws.cancel();
        }
        io.shutdown();
    }
}
