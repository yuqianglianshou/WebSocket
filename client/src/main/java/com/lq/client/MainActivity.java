package com.lq.client;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.textfield.TextInputLayout;
import com.lq.lan.slave.SlaveControlManager.State;
import com.lq.lan.ui.LanUi;
import com.lq.lan.ui.LogAdapter;
import com.lq.lan.ui.StatusCard;

/** 学生端（从机）页面：只做渲染与转发用户操作，状态在 SlaveViewModel，组网逻辑在 lan-core */
public class MainActivity extends AppCompatActivity {

    private SlaveViewModel vm;
    private EditText input;
    private Button send;
    private Button attach;

    private final ActivityResultLauncher<String> pickFile =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) vm.sendFile(uri);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        LanUi.setupEdgeToEdge(this, findViewById(R.id.root));

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        StatusCard statusCard = new StatusCard(findViewById(R.id.status));
        RecyclerView logList = findViewById(R.id.log_list);
        TextView logEmpty = findViewById(R.id.log_empty);
        TextInputLayout inputLayout = findViewById(com.lq.lan.ui.R.id.lan_input_layout);
        input = findViewById(com.lq.lan.ui.R.id.lan_input);
        send = findViewById(com.lq.lan.ui.R.id.lan_send);
        attach = findViewById(com.lq.lan.ui.R.id.lan_attach);
        LogAdapter logAdapter = LanUi.setupLogList(logList);
        inputLayout.setHint(R.string.hint_send);
        attach.setVisibility(View.VISIBLE);

        vm = new ViewModelProvider(this).get(SlaveViewModel.class);

        // 开关表示「是否希望保持连接」，断线后自动重连，开关保持开启
        statusCard.setOnToggleListener(vm::setEnabled);
        send.setOnClickListener(v -> send());
        attach.setOnClickListener(v -> pickFile.launch("*/*"));
        LanUi.onImeSend(input, this::send);
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { updateEnabled(); }
        });

        vm.localIp().observe(this, ip -> toolbar.setSubtitle(ip == null
                ? getString(com.lq.lan.ui.R.string.lan_ip_none)
                : getString(com.lq.lan.ui.R.string.lan_ip_format, ip)));
        vm.status().observe(this, statusCard::render);
        vm.state().observe(this, s -> {
            statusCard.setToggleChecked(s != State.IDLE);
            updateEnabled();
        });
        vm.log().observe(this, list -> {
            logEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
            LanUi.submitLog(logList, logAdapter, list);
        });
    }

    private boolean connected() {
        return vm.state().getValue() == State.CONNECTED;
    }

    private void send() {
        String text = input.getText() == null ? "" : input.getText().toString().trim();
        if (TextUtils.isEmpty(text) || !connected()) return;
        if (vm.send(text)) input.setText(null);
    }

    private void updateEnabled() {
        boolean ok = connected();
        attach.setEnabled(ok);
        send.setEnabled(ok && input.getText() != null && input.getText().toString().trim().length() > 0);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(com.lq.lan.ui.R.menu.lan_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == com.lq.lan.ui.R.id.lan_action_clear) {
            vm.clearLog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
