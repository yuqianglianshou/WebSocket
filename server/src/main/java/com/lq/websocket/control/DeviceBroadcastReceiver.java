package com.lq.websocket.control;

import android.os.Handler;
import android.os.Looper;

import com.lq.websocket.utils.IpUtil;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.Arrays;

/**
 * 接收广播
 */
public class DeviceBroadcastReceiver {


    private static final int BUFFER_LEN = 1024 * 4;
    private volatile boolean needListen = true;

    public interface BroadcastReceiverCallback {
        void onError(String errMsg);

        void onReceive(String senderIp, String message);
    }

    private int port = 0;
    private Handler handler;
    private DatagramSocket server = null;

    public DeviceBroadcastReceiver(boolean isSlave) {
        if (!isSlave) {
            port = ControlConstants.MASTER_LISTEN_PORT;
        } else {
            port = ControlConstants.SLAVE_LISTEN_PORT;
        }
        handler = new Handler(Looper.getMainLooper());
        needListen = true;
    }

    private BroadcastReceiverCallback callback;

    public void setBroadcastReceiveCallback(BroadcastReceiverCallback callback) {
        this.callback = callback;
    }

    private void startReceive() throws IOException {
        final DatagramPacket receive = new DatagramPacket(new byte[BUFFER_LEN], BUFFER_LEN);

        //避免2次启动端口占用异常
        if (server != null) {
            server.close();
            server = null;
        }
        if (server == null) {
            server = new DatagramSocket(null);
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress(port));
        }


        System.out.println("---------------------------------");
        System.out.println("服务端 start listen ......port == " + port);
        System.out.println("---------------------------------");

        while (needListen) {
            server.receive(receive);
            byte[] recvByte = Arrays.copyOfRange(receive.getData(), 0, receive.getLength());
            final String receiveMsg = new String(recvByte);
            System.out.println("服务端 收到 receive msg:" + receiveMsg);
            System.out.println("客户端:" + receive.getAddress().toString() + ":" + receive.getPort());

            final String senderIp = receive.getAddress().getHostAddress();
            String localIP = IpUtil.getHostIP();
            if (senderIp.equals(localIP)) {
                System.out.println("服务端收到自己的消息 myself,ignore");
                continue;
            }

            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (callback != null) {
                        callback.onReceive(senderIp, receiveMsg);
                    }
                }
            });
        }
        server.disconnect();
        server.close();
        System.out.println("end listen ......");
    }

    public void startBroadcastReceive() {

        if (server != null) {
            server.close();
            server = null;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    startReceive();
                } catch (final IOException e) {
                    e.printStackTrace();
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (callback != null) {
                                callback.onError(e.getLocalizedMessage());
                            }
                        }
                    });
                    if (server != null) {
                        server.close();
                        server = null;
                    }
                }
            }
        }).start();
    }

    public void stopReceive() {
        needListen = false;
    }
}
