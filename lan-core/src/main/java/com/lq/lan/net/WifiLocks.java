package com.lq.lan.net;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Build;

/**
 * 组网期间持有的 Wi-Fi 锁：
 * <ul>
 *     <li>MulticastLock：很多机型默认丢弃广播/组播包以省电，不持有就收不到 UDP 发现包</li>
 *     <li>WifiLock：阻止 Wi-Fi 进入省电模式，避免灭屏后长连接延迟飙升甚至断开</li>
 * </ul>
 */
public final class WifiLocks {

    private final WifiManager.MulticastLock multicastLock;
    private final WifiManager.WifiLock wifiLock;

    @SuppressWarnings("deprecation")
    public WifiLocks(Context context, String tag) {
        WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wm == null) {
            multicastLock = null;
            wifiLock = null;
            return;
        }
        multicastLock = wm.createMulticastLock(tag);
        multicastLock.setReferenceCounted(false);
        int mode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                ? WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                : WifiManager.WIFI_MODE_FULL_HIGH_PERF;
        wifiLock = wm.createWifiLock(mode, tag);
        wifiLock.setReferenceCounted(false);
    }

    public void acquire() {
        if (multicastLock != null && !multicastLock.isHeld()) multicastLock.acquire();
        if (wifiLock != null && !wifiLock.isHeld()) wifiLock.acquire();
    }

    public void release() {
        if (multicastLock != null && multicastLock.isHeld()) multicastLock.release();
        if (wifiLock != null && wifiLock.isHeld()) wifiLock.release();
    }
}
