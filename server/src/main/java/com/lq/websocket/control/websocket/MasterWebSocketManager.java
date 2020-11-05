package com.lq.websocket.control.websocket;

import android.util.Log;

import com.koushikdutta.async.AsyncNetworkSocket;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.http.WebSocket;
import com.koushikdutta.async.http.server.AsyncHttpServer;
import com.koushikdutta.async.http.server.AsyncHttpServerRequest;
import com.lq.websocket.control.ControlConstants;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;


public class MasterWebSocketManager {

    public interface MasterSocketManagerCallback {
        void onServerError(Exception ex);

        void onClientConnected(WebSocket clientSocket);

        void onClientDisconnect(WebSocket clientSocket);

        void onMessageReceive(WebSocket clientSocket, String message);

        void onFileReceive(WebSocket clientSocket, String filePath);
    }


    private static final String TAG = "lq_";
    private int clientMaxCount = 3;

    private AsyncHttpServer server = new AsyncHttpServer();
    /**
     * 存储 客户端 实例
     */
    private final List<SlaveClientSocket> socketClients = new ArrayList<>();

    private MasterSocketManagerCallback masterSocketManagerCallback;

    public MasterWebSocketManager(int count) {
        clientMaxCount = count;
    }

    public void setMasterSocketManager(MasterSocketManagerCallback callback) {
        masterSocketManagerCallback = callback;
    }

    public void start() {

        server.websocket("/", ControlConstants.PROTOCOL, callback);

        server.listen(ControlConstants.SERVER_SOCKET_PORT);

        server.setErrorCallback(new CompletedCallback() {
            @Override
            public void onCompleted(Exception ex) {
                if (ex != null) {
                    ex.printStackTrace();
                    Log.e(TAG, "AsyncHttpServer  Error");
                    if (masterSocketManagerCallback != null) {
                        masterSocketManagerCallback.onServerError(ex);
                    }
                }
            }
        });
        Log.i(TAG, "start server and listen");
    }

    public void stop() {
        Log.i(TAG, "stop");

        try {
            for (SlaveClientSocket clientSocket : socketClients) {
                clientSocket.mSocket.close();
            }
        } catch (ConcurrentModificationException e) {
            e.printStackTrace();
            Log.i(TAG, "stop: 关闭客户端出现并发修改异常。");
        }

        socketClients.clear();

        if (server != null) {
            server.stop();
        }
    }

    public void sendMessage(String message) {
        for (SlaveClientSocket clientSocket : socketClients) {
            clientSocket.mSocket.send(message);
        }
    }

    public int getClientCount() {
        return socketClients.size();
    }

    /**
     * 监听客户端的连接
     */
    private AsyncHttpServer.WebSocketRequestCallback callback = new AsyncHttpServer.WebSocketRequestCallback() {
        @Override
        public void onConnected(final WebSocket webSocket, AsyncHttpServerRequest request) {
            Log.i(TAG, "AsyncHttpServer   onConnected" + request.toString());
            AsyncNetworkSocket asyncNetworkSocket = (AsyncNetworkSocket) webSocket.getSocket();
            Log.i(TAG, asyncNetworkSocket.getRemoteAddress().getAddress() + ":" + asyncNetworkSocket.getRemoteAddress().getPort() + " connected");
            if (socketClients.size() >= clientMaxCount) {
                webSocket.close();
                return;
            }

            SlaveClientSocket found = null;
            for (SlaveClientSocket ws : socketClients) {
                AsyncNetworkSocket anws = (AsyncNetworkSocket) ws.mSocket.getSocket();
                if (asyncNetworkSocket.getRemoteAddress().getHostName().equals(anws.getRemoteAddress().getHostName())) {
                    found = ws;
                    break;
                }
            }
            if (found == null) {
                socketClients.add(new SlaveClientSocket(webSocket));
                Log.i(TAG, "AsyncHttpServer   onConnected: 添加客户端 == " + webSocket.toString());
                Log.i(TAG, "AsyncHttpServer   onConnected: 客户端数量 == " + socketClients.size());
                if (masterSocketManagerCallback != null) {
                    masterSocketManagerCallback.onClientConnected(webSocket);
                }
            } else {
                Log.i(TAG, "AsyncHttpServer   slave already connect");
            }
        }
    };


    /**
     * 客户端 类
     */
    private class SlaveClientSocket implements CompletedCallback, WebSocket.StringCallback, DataCallback {
        WebSocket mSocket;

        SlaveClientSocket(WebSocket clientSocket) {
            mSocket = clientSocket;
            mSocket.setStringCallback(this);
            mSocket.setClosedCallback(this);
            mSocket.setDataCallback(this);
        }

        @Override
        public void onCompleted(Exception ex) {
            try {
                if (ex != null) {
                    Log.e(TAG, "客户端 onCompleted Error");
                    ex.printStackTrace();
                } else {
                    Log.d(TAG, "WebSocketClient closed normally.");
                }
            } finally {
                socketClients.remove(this);
                if (masterSocketManagerCallback != null) {
                    //当前客户端失去连接 回调
                    masterSocketManagerCallback.onClientDisconnect(mSocket);
                }
            }
        }

        @Override
        public void onStringAvailable(String s) {
            AsyncNetworkSocket asyncNetworkSocket = (AsyncNetworkSocket) mSocket.getSocket();
            Log.i(TAG, "onStringAvailable  接收客户端消息 from:" + asyncNetworkSocket.getRemoteAddress().getAddress() + ":" + asyncNetworkSocket.getRemoteAddress().getPort());
            Log.i(TAG, "onStringAvailable  接收客户端消息 msg = " + s);
            if (masterSocketManagerCallback != null) {
                masterSocketManagerCallback.onMessageReceive(mSocket, s);
            }
        }

        private static final int BUFFER_LEN = 1024 * 8;

        @Override
        public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
            Log.d(TAG, "onDataAvailable");
            String filePath = getFilePath();
            try {
                FileOutputStream outputStream = new FileOutputStream(new File(filePath));
                long start = System.currentTimeMillis();
                while (bb.hasRemaining()) {
                    int remain = bb.remaining();
                    if (remain > BUFFER_LEN) {
                        outputStream.write(bb.getBytes(BUFFER_LEN));
                    } else {
                        outputStream.write(bb.getBytes(remain));
                    }
                }
                outputStream.flush();
                outputStream.close();
                Log.d(TAG, "receive file:" + filePath);
                Log.d(TAG, "spend " + (System.currentTimeMillis() - start));
                if (masterSocketManagerCallback != null) {
                    masterSocketManagerCallback.onFileReceive(mSocket, filePath);
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    private String getFilePath() {
        String fileName = System.currentTimeMillis() + ".wav";
        String filePath = ControlConstants.RECEIVE_DIR;
        boolean ret = new File(filePath).mkdirs();
        if (!ret) {
//            Log.e(TAG, "make dir error");
        }
        return filePath + fileName;
    }
}
