package com.lq.websocket;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.lq.lan.master.ClientInfo;
import com.lq.lan.ui.LogEntry;
import com.lq.lan.ui.StatusModel;

import java.util.List;

/**
 * 教师端页面状态。服务本身由进程级的 {@link MasterSession} + 前台 {@link MasterService} 承载，
 * 这里只做页面层的转发：退出页面、旋转屏幕都不影响在线的学生端。
 */
public final class MasterViewModel extends AndroidViewModel {

    private final MasterSession session;

    public MasterViewModel(@NonNull Application app) {
        super(app);
        session = MasterSession.get(app);
    }

    public LiveData<StatusModel> status() { return session.status(); }

    public LiveData<Boolean> running() { return session.running(); }

    public LiveData<List<ClientInfo>> clients() { return session.clients(); }

    public LiveData<String> target() { return session.target(); }

    public LiveData<List<LogEntry>> log() { return session.log(); }

    public LiveData<String> localIp() { return session.localIp(); }

    public void setRunning(boolean on) { session.setRunning(on); }

    public void setTarget(@Nullable String clientId) { session.setTarget(clientId); }

    @Nullable
    public ClientInfo targetClient() { return session.targetClient(); }

    public boolean send(String text) { return session.send(text); }

    public void clearLog() { session.clearLog(); }
}
