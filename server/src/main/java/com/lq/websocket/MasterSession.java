package com.lq.websocket;

import android.annotation.SuppressLint;
import android.content.Context;

import androidx.annotation.MainThread;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.lq.lan.LanConfig;
import com.lq.lan.discovery.DiscoveryMessage;
import com.lq.lan.master.ClientInfo;
import com.lq.lan.master.MasterControlManager;
import com.lq.lan.ui.LanUi;
import com.lq.lan.ui.LocalIpLiveData;
import com.lq.lan.ui.LogBuffer;
import com.lq.lan.ui.LogEntry;
import com.lq.lan.ui.StatusModel;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 教师端的进程级会话：持有 MasterControlManager 与界面状态，生命周期跟随进程而不是页面。
 * <ul>
 *     <li>页面（MasterViewModel）只读状态、转发操作；退出页面不会停止服务</li>
 *     <li>服务的启停一律经过 {@link MasterService}：运行期间进程是前台服务优先级，切到后台不会被系统回收</li>
 * </ul>
 * 所有方法都在主线程调用。
 */
@MainThread
public final class MasterSession implements MasterControlManager.Callback {

    // 只持有 ApplicationContext，与进程同寿命，不存在泄漏
    @SuppressLint("StaticFieldLeak")
    private static MasterSession instance;

    public static MasterSession get(Context context) {
        if (instance == null) instance = new MasterSession(context.getApplicationContext());
        return instance;
    }

    private final Context app;
    private final MasterControlManager master;
    private final LogBuffer log = new LogBuffer(500);
    private final MutableLiveData<StatusModel> status = new MutableLiveData<>();
    private final MutableLiveData<Boolean> running = new MutableLiveData<>(false);
    private final MutableLiveData<List<ClientInfo>> clients = new MutableLiveData<>(Collections.<ClientInfo>emptyList());
    /** 当前发送对象，null 表示群发 */
    private final MutableLiveData<String> target = new MutableLiveData<>(null);
    private final LocalIpLiveData localIp;
    private String lastError;

    private MasterSession(Context app) {
        this.app = app;
        master = new MasterControlManager(app, LanConfig.defaults());
        master.setCallback(this);
        localIp = new LocalIpLiveData(app);
        renderStatus();
    }

    public LiveData<StatusModel> status() { return status; }

    public LiveData<Boolean> running() { return running; }

    public LiveData<List<ClientInfo>> clients() { return clients; }

    public LiveData<String> target() { return target; }

    public LiveData<List<LogEntry>> log() { return log.asLiveData(); }

    public LiveData<String> localIp() { return localIp; }

    public LanConfig config() { return master.getConfig(); }

    /** 界面开关入口 */
    public void setRunning(boolean on) {
        if (on) {
            if (master.isRunning()) return;
            lastError = null;
            try {
                MasterService.start(app);
            } catch (RuntimeException e) {
                // 例如 Android 12+ 在后台启动前台服务被拒（ForegroundServiceStartNotAllowedException）
                onForegroundFailed(e);
            }
        } else {
            MasterService.stop(app);
        }
    }

    // ---------------- 仅供 MasterService 调用 ----------------

    void startServer() {
        master.start();
    }

    void stopServer() {
        master.stop();
    }

    void onForegroundFailed(Exception e) {
        lastError = app.getString(R.string.error_foreground, String.valueOf(e.getMessage()));
        log.add(LogEntry.error(lastError));
        running.setValue(false); // 让开关弹回关闭
        renderStatus();
    }

    // ---------------------------------------------------------

    public void setTarget(@Nullable String clientId) {
        target.setValue(clientId);
    }

    @Nullable
    public ClientInfo targetClient() {
        String id = target.getValue();
        if (id == null) return null;
        for (ClientInfo c : master.connectedClients()) if (c.id.equals(id)) return c;
        return null;
    }

    /** @return 是否已发出（用于决定是否清空输入框） */
    public boolean send(String text) {
        if (!master.isRunning()) return false;
        if (master.connectedClients().isEmpty()) {
            log.add(LogEntry.error(app.getString(R.string.log_no_client)));
            return false;
        }
        ClientInfo to = targetClient();
        if (to == null) {
            int n = master.broadcast(text);
            log.add(LogEntry.out(app.getString(R.string.log_to_all, n), text));
            return true;
        }
        if (master.sendTo(to.id, text)) {
            log.add(LogEntry.out(to.name, text));
            return true;
        }
        log.add(LogEntry.error(app.getString(R.string.log_send_failed, to.label())));
        return false;
    }

    public void clearLog() {
        log.clear();
    }

    // ---------------- MasterControlManager.Callback ----------------

    @Override
    public void onRunningChanged(boolean on) {
        running.setValue(on);
        if (lastError == null) log.add(LogEntry.system(app.getString(on ? R.string.log_started : R.string.log_stopped)));
        // 启动失败（端口被占用等）时管理器会自行停止，前台服务也要跟着退出
        if (!on) MasterService.stop(app);
        refreshClients();
    }

    @Override
    public void onServerError(Exception e) {
        lastError = e.getMessage();
        log.add(LogEntry.error(app.getString(R.string.log_error, e.getMessage())));
        renderStatus();
    }

    @Override
    public void onSlaveDiscovered(String ip, DiscoveryMessage info) {
        String who = info.deviceName.isEmpty() ? ip : info.deviceName + " (" + ip + ")";
        log.add(LogEntry.system(app.getString(R.string.log_discovered, who)));
    }

    @Override
    public void onClientConnected(ClientInfo client, int total) {
        log.add(LogEntry.system(app.getString(R.string.log_connected, client.label())));
        refreshClients();
    }

    @Override
    public void onClientDisconnected(ClientInfo client, int total) {
        log.add(LogEntry.system(app.getString(R.string.log_disconnected, client.label())));
        refreshClients();
    }

    @Override
    public void onMessage(ClientInfo client, String message) {
        log.add(LogEntry.in(client.name, message));
    }

    @Override
    public void onFileReceived(ClientInfo client, File file) {
        log.add(LogEntry.in(client.name, app.getString(R.string.log_file,
                file.getName(), LanUi.formatBytes(file.length()), file.getParent())));
    }

    // ----------------------------------------------------------------

    private void refreshClients() {
        List<ClientInfo> list = new ArrayList<>(master.connectedClients());
        Collections.sort(list, (a, b) -> a.label().compareToIgnoreCase(b.label()));
        clients.setValue(list);
        // 单发对象已离线时退回群发
        if (target.getValue() != null && targetClient() == null) target.setValue(null);
        renderStatus();
    }

    private void renderStatus() {
        LanConfig c = master.getConfig();
        if (!master.isRunning()) {
            status.setValue(lastError != null
                    ? new StatusModel(StatusModel.Level.ERROR, app.getString(R.string.status_error), lastError)
                    : new StatusModel(StatusModel.Level.IDLE, app.getString(R.string.status_idle), app.getString(R.string.status_idle_detail)));
            return;
        }
        int n = master.connectedClients().size();
        String detail = app.getString(R.string.status_running_detail, c.maxSlaves, c.wsPort);
        status.setValue(n == 0
                ? new StatusModel(StatusModel.Level.WORKING, app.getString(R.string.status_waiting), detail)
                : new StatusModel(StatusModel.Level.OK, app.getString(R.string.status_running, n), detail));
    }
}
