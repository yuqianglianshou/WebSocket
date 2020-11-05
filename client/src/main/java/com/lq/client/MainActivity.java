package com.lq.client;


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

import com.lq.client.control.SlaveControlManager;
import com.lq.client.utils.DateUtil;
import com.lq.client.utils.IpUtil;

import org.json.JSONObject;


public class MainActivity extends AppCompatActivity {

    private static final String TAG = "lq_client";
    private TextView tv_ip;
    private TextView textView2;
    private CheckBox checkBox;
    private SlaveControlManager slaveControlManager;
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
        slaveControlManager = new SlaveControlManager();

        slaveControlManager.setControlManagerCallback(new SlaveControlManager.SlaveControlManagerCallback() {
            @Override
            public void onFoundMaster(String masterIp, JSONObject masterInfo) {
                stringBuilder.append("发现主机：" + masterIp + " " + masterInfo.toString() + "\n");
                textView2.setText(stringBuilder.toString());
            }

            //主机连接成功回调
            @Override
            public void onConnectedMaster(String masterIp, Exception ex) {

                stringBuilder.append("主机已连接：" + masterIp + "\n");
                textView2.setText(stringBuilder.toString());
            }

            @Override
            public void onDisconnectMaster(String masterIp, Exception ex) {
                slaveControlManager.stop();
                checkBox.setChecked(false);
                stringBuilder.append("主机失去连接：" + masterIp + "\n");
                textView2.setText(stringBuilder.toString());
            }

            @Override
            public void onReceiveMessage(String message) {
                Log.i(TAG, "onMessageReceive: message == " + message);
                stringBuilder.append(message + "\n");
                textView2.setText(stringBuilder.toString());
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

        //  清空显示信息
        findViewById(R.id.btn_clear).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stringBuilder.delete(0, stringBuilder.length());
                textView2.setText(stringBuilder);


            }
        });
        //发送信息
        findViewById(R.id.btn_send).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (!checkBox.isChecked()) {
                    Toast.makeText(MainActivity.this, "未建立连接", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (TextUtils.isEmpty(editText.getText())) {
                    slaveControlManager.send("Hello, I am client ：" + DateUtil.getNowDate());
                } else {
                    slaveControlManager.send(editText.getText().toString() + DateUtil.getNowDate());
                }


            }
        });

        checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    slaveControlManager.start();
                } else {
                    slaveControlManager.stop();
                }
            }
        });

    }


    @Override
    protected void onDestroy() {
        slaveControlManager.stop();
        super.onDestroy();

    }
}