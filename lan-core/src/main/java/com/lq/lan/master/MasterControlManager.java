package com.lq.lan.master;

import android.content.Context;
import android.util.Log;

import com.lq.lan.LanConfig;
import com.lq.lan.discovery.DiscoveryMessage;
import com.lq.lan.discovery.MasterDiscovery;
import com.lq.lan.net.WifiLocks;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 主机（教师端）门面：组合「UDP 发现」与「WebSocket 服务」。
 * <p>
 * 线程模型：所有公开方法都应在主线程调用，所有 Callback 都在主线程回调。
 * 生命周期：建议由 ViewModel / Service 持有，不要放在 Activity 里，否则旋转屏幕就会断开全部连接。
 */
public final class MasterControlManager {

    public interface Callback {
        void onRunningChanged(boolean running);

        void onServerError(Exception e);

        /** UDP 发现阶段首次看到某个从机（还未建立 WebSocket） */
        void onSlaveDiscovered(String ip, DiscoveryMessage info);

        void onClientConnected(ClientInfo client, int total);

        void onClientDisconnected(ClientInfo client, int total);

        void onMessage(ClientInfo client, String message);

        void onFileReceived(ClientInfo client, File file);
    }

    private static final String TAG = "LanMaster";

    private final LanConfig config;
    private final WifiLocks wifiLocks;
    private final MasterDiscovery discovery;
    private final MasterServer server;
    private final Set<String> seenSlaves = new HashSet<>();
    private Callback callback;
    private boolean running;

    public MasterControlManager(Context context, LanConfig config) {
        this.config = config;
        Context app = context.getApplicationContext();
        this.wifiLocks = new WifiLocks(app, TAG);
        this.discovery = new MasterDiscovery(config, (ip, info) -> {
            if (seenSlaves.add(ip) && callback != null) callback.onSlaveDiscovered(ip, info);
        });
        this.server = new MasterServer(config, receiveDir(app), new MasterServer.Listener() {
            @Override
            public void onServerError(Exception e) {
                if (callback != null) callback.onServerError(e);
                // 端口被占用等启动失败场景，整体回到停止态，方便用户重试
                stop();
            }

            @Override
            public void onClientConnected(ClientInfo client) {
                if (callback != null) callback.onClientConnected(client, server.clientCount());
            }

            @Override
            public void onClientDisconnected(ClientInfo client) {
                if (callback != null) callback.onClientDisconnected(client, server.clientCount());
            }

            @Override
            public void onText(ClientInfo client, String text) {
                if (callback != null) callback.onMessage(client, text);
            }

            @Override
            public void onFile(ClientInfo client, File file) {
                if (callback != null) callback.onFileReceived(client, file);
            }
        });
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public LanConfig getConfig() {
        return config;
    }

    public boolean isRunning() {
        return running;
    }

    public void start() {
        if (running) {
            Log.i(TAG, "already running");
            return;
        }
        running = true;
        seenSlaves.clear();
        wifiLocks.acquire();
        server.start();
        discovery.start();
        if (callback != null) callback.onRunningChanged(true);
    }

    public void stop() {
        if (!running) return;
        running = false;
        discovery.stop();
        server.stop();
        wifiLocks.release();
        if (callback != null) callback.onRunningChanged(false);
    }

    /** 宿主销毁时调用，释放线程池；之后实例不可再用 */
    public void release() {
        callback = null;
        stop();
        server.release();
    }

    /** 群发文本，返回送达的连接数 */
    public int broadcast(String message) {
        return running ? server.broadcast(message) : 0;
    }

    /** 单发给指定从机，clientId 取自 {@link ClientInfo#id} */
    public boolean sendTo(String clientId, String message) {
        return running && server.sendTo(clientId, message);
    }

    public List<ClientInfo> connectedClients() {
        return server.clients();
    }

    private static File receiveDir(Context app) {
        // 应用专属目录，无需存储权限
        File base = app.getExternalFilesDir(null);
        if (base == null) base = app.getFilesDir();
        return new File(base, "receive");
    }
}
