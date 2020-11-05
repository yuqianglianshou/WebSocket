package com.lq.websocket.control;

import android.os.Environment;


public class ControlConstants {

    public static final String TEAMID = "Speakin";
    public static final String TASKID = "Test";
    //客户端数量
    public static final int SLAVECOUNT = 80;

    //AsyncHttpServer 监听的端口
    public static final int SERVER_SOCKET_PORT = 8880;
    public static final String PROTOCOL = "speakinchat";

    //客户端接收广播端口号
    public static final int SLAVE_LISTEN_PORT = 8883;
    //服务端接收广播端口号
    public static final int MASTER_LISTEN_PORT = 8882;

    public static final String ROOT = Environment.getExternalStorageDirectory().getAbsolutePath() + "/SpeakInRecorder/";
    public static final String RECEIVE_DIR = ROOT + "receive/";

}
