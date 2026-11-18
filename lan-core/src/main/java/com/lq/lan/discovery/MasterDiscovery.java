package com.lq.lan.discovery;

import android.os.Build;
import android.util.Log;

import com.lq.lan.LanConfig;
import com.lq.lan.util.MainThread;

/**
 * 主机侧发现：周期性广播自己的信息，同时监听从机回包。
 * <p>
 * 达到连接上限后也<b>持续广播</b>，保证掉线的从机能重新发现主机；连接数上限由 WebSocket 服务端把关。
 */
public final class MasterDiscovery {

    public interface Listener {
        /** 收到同组从机的回包（每次都会回调，由上层决定是否去重） */
        void onSlaveSeen(String slaveIp, DiscoveryMessage info);
    }

    private static final String TAG = "LanMasterDiscovery";

    private final LanConfig config;
    private final Listener listener;
    private final String announce;
    private UdpSender sender;
    private UdpReceiver receiver;
    private boolean running;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            sender.broadcast(announce, config.slaveListenPort);
            MainThread.postDelayed(this, config.broadcastIntervalMs);
        }
    };

    public MasterDiscovery(LanConfig config, Listener listener) {
        this.config = config;
        this.listener = listener;
        this.announce = DiscoveryMessage.master(config, Build.MODEL).toJson();
    }

    public void start() {
        if (running) return;
        running = true;
        sender = new UdpSender();
        receiver = new UdpReceiver(config.masterListenPort, new UdpReceiver.Listener() {
            @Override
            public void onPacket(String senderIp, String payload) {
                DiscoveryMessage msg = DiscoveryMessage.parse(payload);
                if (msg != null && msg.role == DiscoveryMessage.Role.SLAVE && msg.sameGroup(config)) {
                    listener.onSlaveSeen(senderIp, msg);
                }
            }

            @Override
            public void onError(Exception e) {
                Log.w(TAG, "receiver error: " + e.getMessage());
            }
        });
        receiver.start();
        tick.run();
    }

    public void stop() {
        if (!running) return;
        running = false;
        MainThread.remove(tick);
        receiver.stop();
        sender.close();
        receiver = null;
        sender = null;
    }
}
