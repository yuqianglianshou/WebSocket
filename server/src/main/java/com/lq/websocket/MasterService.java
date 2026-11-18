package com.lq.websocket;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.PendingIntentCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Observer;

import java.util.List;

/**
 * 教师端前台服务：服务运行期间让进程保持前台优先级，教师切到别的 App、锁屏或从最近任务划掉页面，
 * 全班的连接都不会因为进程被回收而断开。
 * <p>
 * 本身不含组网逻辑，只负责「进程保活 + 常驻通知」，状态都在 {@link MasterSession}。
 * 类型选 connectedDevice（与局域网内的设备保持连接），不受 Android 15 对 dataSync 的 6 小时限制。
 */
public final class MasterService extends Service {

    static final String ACTION_STOP = "com.lq.websocket.action.STOP";
    private static final String CHANNEL_ID = "lan_master";
    private static final int NOTIFICATION_ID = 1;
    /** 学生集中加入时人数变化很快，合并通知更新，避免触发系统的通知限流 */
    private static final long UPDATE_DEBOUNCE_MS = 500;

    static void start(Context context) {
        ContextCompat.startForegroundService(context, new Intent(context, MasterService.class));
    }

    static void stop(Context context) {
        context.stopService(new Intent(context, MasterService.class));
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable updateNotification = this::notifyNow;
    private final Observer<Object> changeObserver = v -> {
        handler.removeCallbacks(updateNotification);
        handler.postDelayed(updateNotification, UPDATE_DEBOUNCE_MS);
    };
    private MasterSession session;
    private boolean foreground;

    @Override
    public void onCreate() {
        super.onCreate();
        session = MasterSession.get(this);
        createChannel();
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!foreground) {
            int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    ? ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE : 0;
            try {
                ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type);
            } catch (RuntimeException e) {
                session.onForegroundFailed(e);
                stopSelf();
                return START_NOT_STICKY;
            }
            foreground = true;
            session.clients().observeForever(changeObserver);
            session.localIp().observeForever(changeObserver);
        }
        session.startServer();
        // 万一进程仍被杀（极端内存压力），系统会重建服务并自动恢复广播，学生端随后自动重连
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(updateNotification);
        if (foreground) {
            session.clients().removeObserver(changeObserver);
            session.localIp().removeObserver(changeObserver);
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
        }
        session.stopServer();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void notifyNow() {
        if (!foreground) return;
        // Android 13+ 未授予通知权限时，前台服务照常运行，只是通知不显示在通知栏
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification());
    }

    private Notification buildNotification() {
        List<?> clients = session.clients().getValue();
        int n = clients == null ? 0 : clients.size();
        String ip = session.localIp().getValue();
        String text = n == 0 ? getString(R.string.status_waiting) : getString(R.string.status_running, n);
        if (ip != null) text = getString(R.string.notif_text_with_ip, text, ip);

        Intent open = new Intent(this, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent content = PendingIntentCompat.getActivity(this, 0, open, 0, false);
        PendingIntent stop = PendingIntentCompat.getService(this, 1,
                new Intent(this, MasterService.class).setAction(ACTION_STOP), 0, false);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_classroom)
                .setContentTitle(getString(R.string.notif_title))
                .setContentText(text)
                .setContentIntent(content)
                .addAction(0, getString(R.string.notif_stop), stop)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(getString(R.string.notif_channel_desc));
        channel.setShowBadge(false);
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.createNotificationChannel(channel);
    }
}
