package com.lq.lan.discovery;

import android.util.Log;

import com.lq.lan.util.MainThread;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;

/**
 * UDP 监听器，独立线程阻塞接收，回调统一切回主线程。
 * <p>
 * <ul>
 *     <li>stop() 直接 close socket 打断阻塞的 receive()，保证线程退出、端口释放</li>
 *     <li>按 UTF-8 解码，中文设备名不乱码</li>
 * </ul>
 */
final class UdpReceiver {

    interface Listener {
        void onPacket(String senderIp, String payload);

        void onError(Exception e);
    }

    private static final String TAG = "LanUdpReceiver";
    private static final int BUFFER_LEN = 4 * 1024;

    private final int port;
    private final Listener listener;
    private volatile DatagramSocket socket;
    private volatile boolean running;
    private Thread thread;

    UdpReceiver(int port, Listener listener) {
        this.port = port;
        this.listener = listener;
    }

    synchronized void start() {
        if (running) return;
        running = true;
        thread = new Thread(this::loop, "lan-udp-recv-" + port);
        thread.setDaemon(true);
        thread.start();
    }

    synchronized void stop() {
        running = false;
        DatagramSocket s = socket;
        if (s != null) s.close();
        if (thread != null) thread.interrupt();
        thread = null;
    }

    private void loop() {
        DatagramSocket s = null;
        try {
            s = new DatagramSocket(null);
            s.setReuseAddress(true);
            s.bind(new InetSocketAddress(port));
            socket = s;
            // stop() 可能发生在 bind 完成之前，那时它拿不到 socket 无法关闭，这里补一次检查
            if (!running) return;
            Log.i(TAG, "listening on udp " + port);

            byte[] buf = new byte[BUFFER_LEN];
            DatagramPacket packet = new DatagramPacket(buf, buf.length);
            while (running) {
                packet.setLength(buf.length);
                s.receive(packet);
                // 不按 IP 过滤「自己发的包」：主从机监听端口不同、上层还会按角色过滤，自己的包本就收不到；
                // 按 IP 过滤反而会让教师端与学生端无法装在同一台设备上联调
                final String sender = packet.getAddress().getHostAddress();
                final String payload = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8);
                MainThread.post(() -> {
                    if (running) listener.onPacket(sender, payload);
                });
            }
        } catch (SocketException e) {
            // stop() 关闭 socket 时会走到这里，属于正常退出
            if (running) dispatchError(e);
        } catch (IOException e) {
            if (running) dispatchError(e);
        } finally {
            if (s != null) s.close();
            socket = null;
            Log.i(TAG, "stop listening udp " + port);
        }
    }

    private void dispatchError(Exception e) {
        Log.w(TAG, "udp receive error", e);
        MainThread.post(() -> listener.onError(e));
    }
}
