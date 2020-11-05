package com.lq.client.control;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.koushikdutta.async.http.WebSocket;
import com.lq.client.control.websocket.SlaveWebSocketManager;

import org.json.JSONObject;

/**
 * Copyright 2017 SpeakIn.Inc
 * Created by west on 2017/10/9.
 */

public class SlaveControlManager implements SlaveSearchManager.SlaveSearchManagerCallback, SlaveWebSocketManager.SlaveSocketManagerCallback {
    private static final String TAG = "lq_";

    public interface SlaveControlManagerCallback {
        void onFoundMaster(String masterIp, JSONObject masterInfo);

        void onConnectedMaster(String masterIp, Exception ex);

        void onDisconnectMaster(String masterIp, Exception ex);

        void onReceiveMessage(String message);
    }

    private SlaveSearchManager searchManager;
    private String masterIp;
    private SlaveWebSocketManager socketManager;
    private Handler handler = new Handler(Looper.getMainLooper());
    private SlaveControlManagerCallback controlManagerCallback;
    private boolean isConnected = false;

    public void setControlManagerCallback(SlaveControlManagerCallback controlManagerCallback) {
        this.controlManagerCallback = controlManagerCallback;
    }

    public void start() {
        startSearchMaster(ControlConstants.TEAMID, ControlConstants.TASKID);
    }

    public void stop() {
        stopSearch();
        stopConnect();
        isConnected = false;
    }

    public boolean send(String message) {
        if (socketManager != null) {
            return socketManager.sendMessage(message);
        }
        Log.d(TAG, "not connected");
        return false;
    }

    public void sendFile(String filePath) {
        if (socketManager != null) {
            socketManager.sendFile(filePath);
        }
    }

    /**
     * 开启广播监听，监听主机的信息
     *
     * @param teamId
     * @param taskId
     */
    public void startSearchMaster(String teamId, String taskId) {
        if (isConnected) {
            Log.d(TAG, "isConnected");
            return;
        }
        stop();
        isConnected = true;
        searchManager = new SlaveSearchManager(teamId, taskId);
        searchManager.setSlaveSearchCallback(this);
        searchManager.start();
    }

    private void stopSearch() {
        if (searchManager != null) {
            searchManager.stop();
            searchManager.setSlaveSearchCallback(null);
        }
        searchManager = null;
    }

    /**
     * 尝试连接主机
     */
    private void startConnectMaster() {
        socketManager = new SlaveWebSocketManager(masterIp, ControlConstants.SERVER_SOCKET_PORT);
        socketManager.setSlaveSocketManagerCallback(this);
        socketManager.startConnect();
    }

    private void stopConnect() {
        if (socketManager != null) {
            socketManager.stopConnect();
            socketManager.setSlaveSocketManagerCallback(null);
        }
        socketManager = null;
    }


    //收到主机广播后的回调
    @Override
    public void onFoundMaster(final String masterIp, final JSONObject masterInfo) {
        Log.i(TAG, "found master" + masterIp);
        this.masterIp = masterIp;

        handler.post(new Runnable() {
            @Override
            public void run() {
                if (controlManagerCallback != null) {
                    controlManagerCallback.onFoundMaster(masterIp, masterInfo);
                }
                //发现主机 关闭 广播监听
                stopSearch();
                //尝试连接主机
                startConnectMaster();
            }
        });
    }


    /**
     * 主机连接成功回调
     *
     * @param socket
     * @param ex
     */
    @Override
    public void onMasterConnected(WebSocket socket, final Exception ex) {
        if (socket != null) {
            Log.d(TAG, "master connected");
            isConnected = true;
        } else {
            Log.d(TAG, "connect to master fail error = " + ex.getLocalizedMessage());
            isConnected = false;
        }
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (controlManagerCallback != null) {
                    controlManagerCallback.onConnectedMaster(SlaveControlManager.this.masterIp, ex);
                }
            }
        });

    }

    @Override
    public void onMasterDisconnect(WebSocket socket, final Exception ex) {
        isConnected = false;
        Log.d(TAG, "master disconnected");
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (controlManagerCallback != null) {
                    controlManagerCallback.onDisconnectMaster(SlaveControlManager.this.masterIp, ex);
                }
            }
        });

    }

    @Override
    public void onReceiveMessage(final String message) {
        Log.d(TAG, "receive: " + message);
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (controlManagerCallback != null) {
                    controlManagerCallback.onReceiveMessage(message);
                }
            }
        });
    }
}
