package com.lq.websocket;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
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
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputLayout;
import com.lq.lan.master.ClientInfo;
import com.lq.lan.ui.LanUi;
import com.lq.lan.ui.LogAdapter;
import com.lq.lan.ui.StatusCard;

import java.util.List;

/** 教师端（主机）页面：只做渲染与转发用户操作；服务由前台 MasterService 承载，退出页面不会断开学生端 */
public class MainActivity extends AppCompatActivity {

    private MasterViewModel vm;
    private MaterialToolbar toolbar;
    private StatusCard statusCard;
    private View targetBar;
    private ChipGroup targetChips;
    private RecyclerView logList;
    private LogAdapter logAdapter;
    private TextView logEmpty;
    private TextInputLayout inputLayout;
    private EditText input;
    private Button send;
    /** 程序化重建 chips 时屏蔽选择回调 */
    private boolean bindingChips;
    /** 通知权限只影响常驻通知是否可见，不影响服务运行，所以不等待授权结果 */
    private final ActivityResultLauncher<String> notificationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> { });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        LanUi.setupEdgeToEdge(this, findViewById(R.id.root));

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        statusCard = new StatusCard(findViewById(R.id.status));
        targetBar = findViewById(R.id.target_bar);
        targetChips = findViewById(R.id.target_chips);
        logList = findViewById(R.id.log_list);
        logEmpty = findViewById(R.id.log_empty);
        inputLayout = findViewById(com.lq.lan.ui.R.id.lan_input_layout);
        input = findViewById(com.lq.lan.ui.R.id.lan_input);
        send = findViewById(com.lq.lan.ui.R.id.lan_send);
        logAdapter = LanUi.setupLogList(logList);

        vm = new ViewModelProvider(this).get(MasterViewModel.class);

        statusCard.setOnToggleListener(on -> {
            if (on) requestNotificationPermission();
            vm.setRunning(on);
        });
        send.setOnClickListener(v -> send());
        LanUi.onImeSend(input, this::send);
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { updateSendEnabled(); }
        });
        targetChips.setOnCheckedStateChangeListener((group, ids) -> {
            if (bindingChips || ids.isEmpty()) return;
            Object tag = group.findViewById(ids.get(0)).getTag();
            vm.setTarget(tag instanceof String ? (String) tag : null);
        });

        vm.localIp().observe(this, ip -> toolbar.setSubtitle(ip == null
                ? getString(com.lq.lan.ui.R.string.lan_ip_none)
                : getString(com.lq.lan.ui.R.string.lan_ip_format, ip)));
        vm.status().observe(this, statusCard::render);
        vm.running().observe(this, on -> {
            statusCard.setToggleChecked(on);
            updateSendEnabled();
        });
        vm.clients().observe(this, this::renderTargets);
        vm.target().observe(this, id -> {
            renderTargets(vm.clients().getValue());
            ClientInfo c = vm.targetClient();
            inputLayout.setHint(c == null ? getString(R.string.hint_all) : getString(R.string.hint_single, c.name));
        });
        vm.log().observe(this, list -> {
            logEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
            LanUi.submitLog(logList, logAdapter, list);
        });
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    private void send() {
        String text = input.getText() == null ? "" : input.getText().toString().trim();
        if (TextUtils.isEmpty(text)) return;
        if (vm.send(text)) input.setText(null);
    }

    private void updateSendEnabled() {
        boolean running = Boolean.TRUE.equals(vm.running().getValue());
        send.setEnabled(running && input.getText() != null && input.getText().toString().trim().length() > 0);
    }

    private void renderTargets(List<ClientInfo> clients) {
        if (clients == null) return;
        targetBar.setVisibility(clients.isEmpty() ? View.GONE : View.VISIBLE);
        String selected = vm.target().getValue();
        bindingChips = true;
        targetChips.removeAllViews();
        targetChips.addView(chip(getString(R.string.target_all, clients.size()), null, selected == null));
        for (ClientInfo c : clients) {
            targetChips.addView(chip(c.name, c.id, c.id.equals(selected)));
        }
        bindingChips = false;
    }

    private Chip chip(String text, String tag, boolean checked) {
        Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_target_chip, targetChips, false);
        chip.setId(View.generateViewId());
        chip.setText(text);
        chip.setTag(tag);
        chip.setChecked(checked);
        return chip;
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
