package com.lq.client;

import android.app.Application;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.lq.lan.LanConfig;
import com.lq.lan.discovery.DiscoveryMessage;
import com.lq.lan.slave.SlaveControlManager;
import com.lq.lan.slave.SlaveControlManager.State;
import com.lq.lan.ui.LanUi;
import com.lq.lan.ui.LocalIpLiveData;
import com.lq.lan.ui.LogBuffer;
import com.lq.lan.ui.LogEntry;
import com.lq.lan.ui.StatusModel;
import com.lq.lan.util.MainThread;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 学生端界面状态。持有 SlaveControlManager，旋转屏幕时连接不断开；页面真正退出（onCleared）时才释放。
 */
public final class SlaveViewModel extends AndroidViewModel implements SlaveControlManager.Callback {

    private final SlaveControlManager slave;
    private final LogBuffer log = new LogBuffer(500);
    private final MutableLiveData<StatusModel> status = new MutableLiveData<>();
    private final MutableLiveData<State> state = new MutableLiveData<>(State.IDLE);
    private final LocalIpLiveData localIp;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private long retryDelayMs;

    public SlaveViewModel(@NonNull Application app) {
        super(app);
        slave = new SlaveControlManager(app, LanConfig.defaults());
        slave.setCallback(this);
        localIp = new LocalIpLiveData(app);
        renderStatus();
    }

    public LiveData<StatusModel> status() { return status; }

    public LiveData<State> state() { return state; }

    public LiveData<java.util.List<LogEntry>> log() { return log.asLiveData(); }

    public LiveData<String> localIp() { return localIp; }

    public void setEnabled(boolean on) {
        if (on) slave.start();
        else slave.stop();
    }

    public boolean send(String text) {
        if (slave.send(text)) {
            log.add(LogEntry.out(s(R.string.me_to_master), text));
            return true;
        }
        log.add(LogEntry.error(s(R.string.log_send_failed)));
        return false;
    }

    /** 读取用户选择的文件并以二进制帧发送，读文件在后台线程 */
    public void sendFile(Uri uri) {
        final String name = displayName(uri);
        io.execute(() -> {
            long max = SlaveControlManager.maxSendBytes();
            byte[] data;
            try (InputStream in = getApplication().getContentResolver().openInputStream(uri)) {
                if (in == null) throw new IOException("cannot open " + uri);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[16 * 1024];
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                    if (out.size() > max) {
                        MainThread.post(() -> log.add(LogEntry.error(getApplication().getString(
                                R.string.log_file_too_large, name, LanUi.formatBytes(max)))));
                        return;
                    }
                }
                data = out.toByteArray();
            } catch (IOException | SecurityException e) {
                MainThread.post(() -> log.add(LogEntry.error(getApplication().getString(
                        R.string.log_file_read_failed, e.getMessage()))));
                return;
            }
            final byte[] payload = data;
            MainThread.post(() -> {
                if (slave.sendBytes(payload)) {
                    log.add(LogEntry.out(s(R.string.me_to_master), getApplication().getString(
                            R.string.log_file_sent, name, LanUi.formatBytes(payload.length))));
                } else {
                    log.add(LogEntry.error(s(R.string.log_send_failed)));
                }
            });
        });
    }

    public void clearLog() {
        log.clear();
    }

    @Override
    protected void onCleared() {
        slave.release();
        io.shutdownNow();
    }

    // ---------------- SlaveControlManager.Callback ----------------

    @Override
    public void onStateChanged(State s, String masterIp) {
        State prev = state.getValue();
        state.setValue(s);
        if (s == State.CONNECTED) {
            log.add(LogEntry.system(getApplication().getString(R.string.log_connected, masterIp)));
        } else if (s == State.IDLE && prev != State.IDLE) {
            log.add(LogEntry.system(s(R.string.log_stopped)));
        }
        renderStatus();
    }

    @Override
    public void onMasterFound(String masterIp, DiscoveryMessage info) {
        String who = info.deviceName.isEmpty() ? masterIp : info.deviceName + " (" + masterIp + ")";
        log.add(LogEntry.system(getApplication().getString(R.string.log_found, who)));
    }

    @Override
    public void onMessage(String message) {
        log.add(LogEntry.in(s(R.string.peer_master), message));
    }

    @Override
    public void onRetryScheduled(long delayMs, Exception cause) {
        retryDelayMs = delayMs;
        log.add(LogEntry.system(getApplication().getString(R.string.log_retry,
                s(R.string.log_lost), seconds(delayMs))));
        renderStatus();
    }

    // ----------------------------------------------------------------

    private void renderStatus() {
        String ip = slave.getMasterIp();
        switch (slave.getState()) {
            case SEARCHING:
                status.setValue(new StatusModel(StatusModel.Level.WORKING,
                        s(R.string.status_searching), s(R.string.status_searching_detail)));
                break;
            case CONNECTING:
                status.setValue(new StatusModel(StatusModel.Level.WORKING, s(R.string.status_connecting), ip, true));
                break;
            case CONNECTED:
                status.setValue(new StatusModel(StatusModel.Level.OK, s(R.string.status_connected), ip));
                break;
            case WAITING:
                status.setValue(new StatusModel(StatusModel.Level.WARN, s(R.string.status_waiting),
                        getApplication().getString(R.string.status_waiting_detail, seconds(retryDelayMs))));
                break;
            default:
                status.setValue(new StatusModel(StatusModel.Level.IDLE,
                        s(R.string.status_idle), s(R.string.status_idle_detail)));
        }
    }

    private static int seconds(long ms) {
        return (int) Math.max(1, Math.round(ms / 1000.0));
    }

    private String displayName(Uri uri) {
        try (Cursor c = getApplication().getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                String n = c.getString(0);
                if (n != null) return n;
            }
        } catch (RuntimeException ignore) {
            // 部分 provider 不支持查询
        }
        String last = uri.getLastPathSegment();
        return last != null ? last : "file";
    }

    private String s(int res) {
        return getApplication().getString(res);
    }
}
