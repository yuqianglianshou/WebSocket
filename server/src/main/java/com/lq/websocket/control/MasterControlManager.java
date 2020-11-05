package com.lq.websocket.control;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.koushikdutta.async.AsyncNetworkSocket;
import com.koushikdutta.async.http.WebSocket;
import com.lq.websocket.control.websocket.MasterWebSocketManager;

import org.json.JSONObject;

import java.util.List;


public class MasterControlManager implements MasterSearchManager.MasterSearchManagerCallback, MasterWebSocketManager.MasterSocketManagerCallback {

    public interface MasterControlManagerCallback {
        void onServerError(Exception ex);

        void onClientConnected(String clientSocket);

        void onClientDisconnect(String clientSocket);

        void onMessageReceive(String clientSocket, String message);

        void onReceiveFile(String clientSocket, String filePath);
    }

    private static final String TAG = "lq_server";
    private MasterSearchManager searchManager;
    private MasterWebSocketManager socketManager;
    private Handler handler = new Handler(Looper.getMainLooper());
    private MasterControlManagerCallback controlManagerCallback;
    private boolean isRunning = false;

    public void setControlManagerCallback(MasterControlManagerCallback controlManagerCallback) {
        this.controlManagerCallback = controlManagerCallback;
    }

    public void start() {
        start(ControlConstants.SLAVECOUNT, ControlConstants.TEAMID, ControlConstants.TASKID);
    }

    public void stop() {
        stopSearch();
        stopMaster();
        isRunning = false;
    }

    public void start(int slaveCount, String teamId, String taskId) {
        if (isRunning) {
            Log.i(TAG, "is running");
            return;
        }
        searchManager = new MasterSearchManager(slaveCount, teamId, taskId);
        searchManager.setSearchCallback(this);
        searchManager.start();

        socketManager = new MasterWebSocketManager(slaveCount);
        socketManager.setMasterSocketManager(this);
        socketManager.start();

    }

    public void send(String message) {
        if (socketManager != null) {
            socketManager.sendMessage(message);
        }
    }

    private void stopMaster() {
        if (socketManager != null) {
            socketManager.stop();
            socketManager.setMasterSocketManager(null);
        }
        socketManager = null;
    }

    private void stopSearch() {
        if (searchManager != null) {
            searchManager.stop();
            searchManager.setSearchCallback(null);
        }
        searchManager = null;
    }

    /**
     * MasterSearchManagerCallback 回调，发现客户端
     *
     * @param slaveIp
     * @param slaveInfo
     */
    @Override
    public void onFoundNewSlave(String slaveIp, JSONObject slaveInfo) {
        Log.i(TAG, "发现从机 " + slaveIp + " info=" + slaveInfo);
    }

    /**
     * MasterSearchManagerCallback 回调，客户端已达上限，返回所有客户端list
     *
     * @param slaveIpList
     */
    @Override
    public void onFoundSlaves(List<String> slaveIpList) {
        Log.i(TAG, "slave count = " + slaveIpList.size());
        String msg = "发现从机：\n";
        for (String ip : slaveIpList) {
            msg += "IP地址：" + ip + "\n";
        }
        Log.i(TAG, msg);

        handler.post(new Runnable() {
            @Override
            public void run() {
                stopSearch();
            }
        });
    }

    @Override
    public void onServerError(final Exception ex) {
        Log.i(TAG, "start server error " + ex.getLocalizedMessage());
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (controlManagerCallback != null) {
                    controlManagerCallback.onServerError(ex);
                }
            }
        });

        isRunning = false;
    }

    @Override
    public void onClientConnected(WebSocket clientSocket) {
        final AsyncNetworkSocket workSocket = (AsyncNetworkSocket) clientSocket.getSocket();
        Log.i(TAG, "client connected: " + workSocket.getRemoteAddress().getHostName());
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (controlManagerCallback != null) {
                    controlManagerCallback.onClientConnected(workSocket.getRemoteAddress().getHostName());
                }
                if (socketManager.getClientCount() == ControlConstants.SLAVECOUNT) {
                    stopSearch();
                }
            }
        });
    }

    @Override
    public void onClientDisconnect(WebSocket clientSocket) {
        final AsyncNetworkSocket workSocket = (AsyncNetworkSocket) clientSocket.getSocket();
        Log.d(TAG, "client onClientDisconnect: " + workSocket.getRemoteAddress().getHostName());
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (controlManagerCallback != null) {
                    controlManagerCallback.onClientDisconnect(workSocket.getRemoteAddress().getHostName());
                }
            }
        });

    }

    @Override
    public void onMessageReceive(WebSocket clientSocket, final String message) {
        final AsyncNetworkSocket workSocket = (AsyncNetworkSocket) clientSocket.getSocket();
        Log.i(TAG, "receive: " + message + "from: " + workSocket.getRemoteAddress().getHostName());
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (controlManagerCallback != null) {
                    controlManagerCallback.onMessageReceive(workSocket.getRemoteAddress().getHostName(), message);
                }
            }
        });
    }

    @Override
    public void onFileReceive(WebSocket clientSocket, final String filePath) {
        final AsyncNetworkSocket workSocket = (AsyncNetworkSocket) clientSocket.getSocket();
        Log.d(TAG, "receive: " + filePath + "from: " + workSocket.getRemoteAddress().getHostName());
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (controlManagerCallback != null) {
                    controlManagerCallback.onReceiveFile(workSocket.getRemoteAddress().getHostName(), filePath);
                }
            }
        });
    }
}
