package com.lq.lan.slave;

import android.content.Context;
import android.util.Log;

import com.lq.lan.LanConfig;
import com.lq.lan.LanProtocol;
import com.lq.lan.discovery.DiscoveryMessage;
import com.lq.lan.discovery.SlaveDiscovery;
import com.lq.lan.net.DeviceIdentity;
import com.lq.lan.net.WifiLocks;
import com.lq.lan.util.MainThread;

import java.io.File;
import java.util.Random;

/**
 * 从机（学生端）门面，内部是一个显式状态机：
 * <pre>
 *   IDLE --start--> SEARCHING --发现主机--> CONNECTING --成功--> CONNECTED
 *                       ^                    ^      |               |
 *                       |   (直连上次的主机)  |      | 失败           | 断开
 *                       +------------------ WAITING <---------------+
 * </pre>
 * 断开后进入 WAITING，按指数退避（含随机抖动，避免几十台学生端同时冲击主机）等待：
 * 前 {@link #DIRECT_RETRIES} 次直接重连上次的主机，省掉等待广播的时间；仍失败则回到 SEARCHING 重新发现
 * （主机 IP 可能已变化）。只要没调用 stop() 就会一直尝试。
 * <p>
 * 线程模型：公开方法在主线程调用，Callback 在主线程回调。
 * 生命周期：建议由 ViewModel / Service 持有。
 */
public final class SlaveControlManager {

    public enum State {IDLE, SEARCHING, CONNECTING, CONNECTED, WAITING}

    public interface Callback {
        void onStateChanged(State state, String masterIp);

        void onMasterFound(String masterIp, DiscoveryMessage info);

        void onMessage(String message);

        /** 断线或连接失败，delayMs 后重试 */
        void onRetryScheduled(long delayMs, Exception cause);
    }

    private static final String TAG = "LanSlave";
    static final int DIRECT_RETRIES = 2;
    static final long MAX_BACKOFF_MS = 30_000;

    private final LanConfig config;
    private final DeviceIdentity identity;
    private final WifiLocks wifiLocks;
    private final SlaveDiscovery discovery;
    private final Random random = new Random();
    private Callback callback;
    private State state = State.IDLE;
    private SlaveClient client;

    /** 最近一次连上的主机，用于快速直连 */
    private String masterIp;
    private int masterPort;
    private int failures;

    private final Runnable retry = this::retryNow;

    public SlaveControlManager(Context context, LanConfig config) {
        this.config = config;
        this.identity = DeviceIdentity.of(context);
        this.wifiLocks = new WifiLocks(context.getApplicationContext(), TAG);
        this.discovery = new SlaveDiscovery(config, new SlaveDiscovery.Listener() {
            @Override
            public void onMasterFound(String ip, DiscoveryMessage info) {
                handleMasterFound(ip, info);
            }

            @Override
            public void onError(Exception e) {
                Log.w(TAG, "discovery error", e);
                scheduleRetry(e);
            }
        });
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public State getState() {
        return state;
    }

    public String getMasterIp() {
        return masterIp;
    }

    public void start() {
        if (state != State.IDLE) return;
        failures = 0;
        wifiLocks.acquire();
        enterSearching();
    }

    public void stop() {
        if (state == State.IDLE) return;
        MainThread.remove(retry);
        discovery.stop();
        closeClient();
        wifiLocks.release();
        masterIp = null;
        setState(State.IDLE);
    }

    /** 宿主销毁时调用 */
    public void release() {
        callback = null;
        stop();
    }

    public boolean send(String message) {
        return client != null && client.send(message);
    }

    public boolean sendFile(File file) {
        return client != null && client.sendFile(file);
    }

    /** 以单个二进制帧发送，上限 {@link SlaveClient#MAX_FILE_BYTES} */
    public boolean sendBytes(byte[] data) {
        return client != null && client.sendBytes(data);
    }

    public static long maxSendBytes() {
        return SlaveClient.MAX_FILE_BYTES;
    }

    // ---------------- 状态迁移（均在主线程） ----------------

    private void enterSearching() {
        closeClient();
        setState(State.SEARCHING);
        discovery.start();
    }

    private void handleMasterFound(String ip, DiscoveryMessage info) {
        // 主机每个周期都会广播，只有 SEARCHING 态才处理，防止重复发起连接
        if (state != State.SEARCHING) return;
        discovery.stop();
        if (callback != null) callback.onMasterFound(ip, info);
        connect(ip, info.wsPort > 0 ? info.wsPort : config.wsPort);
    }

    private void connect(String ip, int port) {
        closeClient();
        masterIp = ip;
        masterPort = port;
        setState(State.CONNECTING);
        client = new SlaveClient(config, identity, new SlaveClient.Listener() {
            @Override
            public void onConnected() {
                failures = 0;
                setState(State.CONNECTED);
            }

            @Override
            public void onConnectFailed(boolean rejected, Exception e) {
                Log.w(TAG, "connect " + ip + " failed, rejected=" + rejected + ": " + e.getMessage());
                if (rejected) failures = Math.max(failures, 4);
                scheduleRetry(e);
            }

            @Override
            public void onClosed(int code, Exception e) {
                Log.i(TAG, "disconnected from " + ip + ", code=" + code);
                // 主机已满：直接跳到较长的退避，别反复敲门
                if (code == LanProtocol.CLOSE_SERVER_FULL) failures = Math.max(failures, 4);
                scheduleRetry(e);
            }

            @Override
            public void onText(String text) {
                if (callback != null) callback.onMessage(text);
            }
        });
        client.connect(ip, port);
    }

    private void scheduleRetry(Exception cause) {
        if (state == State.IDLE) return;
        discovery.stop();
        closeClient();
        failures++;
        long delay = backoffMs(config.reconnectDelayMs, failures, random.nextDouble());
        setState(State.WAITING);
        if (callback != null) callback.onRetryScheduled(delay, cause);
        MainThread.remove(retry);
        MainThread.postDelayed(retry, delay);
    }

    private void retryNow() {
        if (state != State.WAITING) return;
        if (masterIp != null && failures <= DIRECT_RETRIES) {
            connect(masterIp, masterPort);
        } else {
            enterSearching();
        }
    }

    /** base * 2^(n-1)，封顶 MAX_BACKOFF_MS，再叠加 ±20% 抖动 */
    static long backoffMs(long base, int failures, double rand01) {
        int exp = Math.min(Math.max(failures - 1, 0), 10);
        long delay = Math.min(base << exp, MAX_BACKOFF_MS);
        double jitter = 0.8 + 0.4 * rand01;
        return (long) (delay * jitter);
    }

    private void closeClient() {
        if (client != null) {
            client.close();
            client = null;
        }
    }

    private void setState(State s) {
        if (state == s) return;
        state = s;
        if (callback != null) callback.onStateChanged(s, masterIp);
    }
}
