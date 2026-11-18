package com.lq.lan.discovery;

import android.util.Log;

import com.lq.lan.net.NetUtils;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * UDP 广播发送器。
 * <p>
 * 单线程串行发送并复用 socket。每次同时发往 255.255.255.255 和各网卡定向广播地址，提高在热点/多网卡/路由器屏蔽受限广播场景下的送达率。
 */
final class UdpSender {

    private static final String TAG = "LanUdpSender";

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "lan-udp-send");
        t.setDaemon(true);
        return t;
    });
    private DatagramSocket socket;

    void broadcast(final String message, final int port) {
        if (executor.isShutdown()) return;
        executor.execute(() -> {
            byte[] data = message.getBytes(StandardCharsets.UTF_8);
            for (InetAddress target : NetUtils.broadcastTargets()) {
                send(data, target, port);
            }
        });
    }

    /** 定向单播回包（从机回复主机时用，比广播更可靠） */
    void unicast(final String message, final String ip, final int port) {
        if (executor.isShutdown()) return;
        executor.execute(() -> {
            try {
                send(message.getBytes(StandardCharsets.UTF_8), InetAddress.getByName(ip), port);
            } catch (IOException e) {
                Log.w(TAG, "unicast to " + ip + " failed", e);
            }
        });
    }

    private void send(byte[] data, InetAddress target, int port) {
        try {
            if (socket == null || socket.isClosed()) {
                socket = new DatagramSocket();
                socket.setBroadcast(true);
            }
            socket.send(new DatagramPacket(data, data.length, target, port));
        } catch (IOException e) {
            // 某个网卡不可达不影响其它目标
            Log.d(TAG, "send to " + target + ":" + port + " failed: " + e.getMessage());
        }
    }

    void close() {
        executor.execute(() -> {
            if (socket != null) socket.close();
            socket = null;
        });
        executor.shutdown();
    }
}
