package com.lq.lan.discovery;

import android.os.Build;
import android.util.Log;

import com.lq.lan.LanConfig;

/**
 * 从机侧发现：监听主机广播，收到同组主机的包后单播回复自己的信息，并通知上层。
 */
public final class SlaveDiscovery {

    public interface Listener {
        void onMasterFound(String masterIp, DiscoveryMessage info);

        void onError(Exception e);
    }

    private static final String TAG = "LanSlaveDiscovery";

    private final LanConfig config;
    private final Listener listener;
    private final String reply;
    private UdpSender sender;
    private UdpReceiver receiver;
    private boolean running;

    public SlaveDiscovery(LanConfig config, Listener listener) {
        this.config = config;
        this.listener = listener;
        this.reply = DiscoveryMessage.slave(config, Build.MODEL).toJson();
    }

    public void start() {
        if (running) return;
        running = true;
        sender = new UdpSender();
        receiver = new UdpReceiver(config.slaveListenPort, new UdpReceiver.Listener() {
            @Override
            public void onPacket(String senderIp, String payload) {
                DiscoveryMessage msg = DiscoveryMessage.parse(payload);
                if (msg == null || msg.role != DiscoveryMessage.Role.MASTER) return;
                // 只回复同组主机
                if (!msg.sameGroup(config)) {
                    Log.d(TAG, "ignore master of other group: " + senderIp);
                    return;
                }
                sender.unicast(reply, senderIp, config.masterListenPort);
                listener.onMasterFound(senderIp, msg);
            }

            @Override
            public void onError(Exception e) {
                listener.onError(e);
            }
        });
        receiver.start();
    }

    public void stop() {
        if (!running) return;
        running = false;
        receiver.stop();
        sender.close();
        receiver = null;
        sender = null;
    }
}
