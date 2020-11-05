package com.lq.websocket;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.lq.websocket.control.MasterControlManager;
import com.lq.websocket.utils.DateUtil;
import com.lq.websocket.utils.IpUtil;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "lq_server";
    private TextView tv_ip;
    private TextView textView2;
    private CheckBox checkBox;
    private MasterControlManager masterControlManager;
    private EditText editText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tv_ip = findViewById(R.id.tv_ip);
        textView2 = findViewById(R.id.textView2);
        checkBox = findViewById(R.id.checkbox);
        editText = findViewById(R.id.et_message);

        initData();
        initView();

        refreshIP();
    }


    private void refreshIP() {
        String ip = IpUtil.getHostIP();

        tv_ip.setText("本机IP: " + ip);
    }

    private StringBuilder stringBuilder = new StringBuilder();

    private void initData() {
        masterControlManager = new MasterControlManager();
        masterControlManager.setControlManagerCallback(new MasterControlManager.MasterControlManagerCallback() {
            @Override
            public void onServerError(Exception ex) {
                textView2.setText("server error: " + ex.getLocalizedMessage());
            }

            @Override
            public void onClientConnected(String clientSocket) {
                stringBuilder.append(clientSocket + "  客户端连接 \n");
                textView2.setText(stringBuilder.toString());
            }

            @Override
            public void onClientDisconnect(String clientSocket) {
                stringBuilder.append(clientSocket + " 客户端 失去连接 \n");
                textView2.setText(stringBuilder.toString());
            }

            //接收客户端消息
            @Override
            public void onMessageReceive(String clientSocket, String message) {
                Log.i(TAG, "onMessageReceive: clientSocket == " + clientSocket);
                Log.i(TAG, "onMessageReceive: message == " + message);
                stringBuilder.append(clientSocket + ":" + message + "\n");
                textView2.setText(stringBuilder.toString());
            }

            @Override
            public void onReceiveFile(String clientSocket, String filePath) {
                Toast.makeText(MainActivity.this, "receive file" + filePath, Toast.LENGTH_SHORT).show();
            }
        });


    }

    private void initView() {
        tv_ip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                refreshIP();
            }
        });

        /**
         * 清空显示
         */
        findViewById(R.id.btn_clear).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stringBuilder.delete(0, stringBuilder.length());
                textView2.setText(stringBuilder);

            }
        });
        /**
         * 发消息
         */
        findViewById(R.id.btn_send).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!checkBox.isChecked()) {
                    Toast.makeText(MainActivity.this, "未建立连接", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (TextUtils.isEmpty(editText.getText())) {
                    masterControlManager.send("Hello, I am server ：" + DateUtil.getNowDate());
                } else {
                    masterControlManager.send(editText.getText().toString() + "  " + DateUtil.getNowDate());
                }
            }
        });
        checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    masterControlManager.start();
                } else {
                    masterControlManager.stop();
                }
            }
        });
    }


    @Override
    protected void onDestroy() {
        masterControlManager.stop();
        super.onDestroy();

    }
}