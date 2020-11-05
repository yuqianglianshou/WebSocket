package com.lq.websocket.control;

import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 每 BROADCAST_INTERVAL 秒 发送一次广播，监听此端口的客户端收到广播后可以拿到此服务端地址
 */
public class MasterSearchManager implements DeviceBroadcastReceiver.BroadcastReceiverCallback {

    private static final String TAG = "MasterSearchManager";
    private DeviceBroadcastSender broadcastSender;
    private DeviceBroadcastReceiver broadcastReceiver;
    private static final int BROADCAST_INTERVAL = 5 * 1000;

    public interface MasterSearchManagerCallback {
        void onFoundNewSlave(String slaveIp, JSONObject slaveInfo);

        void onFoundSlaves(List<String> slaveIpList);
    }

    private int minDeviceCount = 1;
    private Handler handler;
    //循环广播停止标志
    private volatile boolean stop = false;
    private List<String> ipList;
    private String teamId;
    private String taskId;

    public MasterSearchManager(int minDeviceCount, String teamId, String taskId) {
        ipList = new ArrayList<>(5);
        handler = new Handler(Looper.getMainLooper());
        this.teamId = teamId;
        this.taskId = taskId;
        this.minDeviceCount = minDeviceCount;
    }

    private MasterSearchManagerCallback callback;

    public void setSearchCallback(MasterSearchManagerCallback callback) {
        this.callback = callback;
    }

    private String getSendMsg() {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("type", "master");
            jsonObject.put("teamId", this.teamId);
            jsonObject.put("port", ControlConstants.SERVER_SOCKET_PORT);
            jsonObject.put("taskId", this.taskId);
            jsonObject.put("deviceName", Build.MODEL);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return jsonObject.toString();
    }

    /**
     * 不间断发送广播
     */
    private Runnable broadcastRunnable = new Runnable() {
        @Override
        public void run() {
            if (stop) {
                return;
            }
            if (broadcastSender != null) {
                broadcastSender.sendBroadcastData(getSendMsg());
                handler.postDelayed(this, BROADCAST_INTERVAL);
            }
        }
    };

    public void start() {
        stop = false;
        //广播接收开启
        broadcastReceiver = new DeviceBroadcastReceiver(false);
        broadcastReceiver.setBroadcastReceiveCallback(this);

        //广播发送
        broadcastSender = new DeviceBroadcastSender(false);
        //广播接收开启
        broadcastReceiver.startBroadcastReceive();
        broadcastSender.sendBroadcastData(getSendMsg());
        //循环广播开启
        handler.postDelayed(broadcastRunnable, BROADCAST_INTERVAL);
    }

    public void stop() {
        Log.d(TAG, "stop search");
        stop = true;
        broadcastReceiver.stopReceive();
        broadcastReceiver.setBroadcastReceiveCallback(null);
        broadcastReceiver = null;

        broadcastSender = null;
        handler.removeCallbacks(broadcastRunnable);
    }

    @Override
    public void onError(String errMsg) {
        Log.d(TAG, "errMsg =" + errMsg);
    }

    //收到客户端广播回调
    @Override
    public void onReceive(String senderIp, String message) {
        Log.d(TAG, "onReceive 收到客户端广播回调:" + senderIp + "   message ：" + message);
        try {
            JSONObject jsonObj = new JSONObject(message);
            String type = jsonObj.optString("type");
            if (type != null && type.equals("slave")) {
                String team = jsonObj.optString("teamId");
                String task = jsonObj.optString("taskId");
                if (!TextUtils.isEmpty(team) && task.equals(teamId) && !TextUtils.isEmpty(task) && task.equals(taskId)) {
                    if (!ipList.contains(senderIp)) {
                        ipList.add(senderIp);
                        if (callback != null) {
                            //发现新的客户端回调
                            callback.onFoundNewSlave(senderIp, jsonObj);
                        }
                    }
                    if (ipList.size() == this.minDeviceCount) {
                        if (callback != null) {
                            //客户端数量达到上限回掉，返回所有客户端信息
                            callback.onFoundSlaves(ipList);
                        }
                    }
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}
