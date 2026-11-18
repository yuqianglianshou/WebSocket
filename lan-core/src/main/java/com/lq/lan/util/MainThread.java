package com.lq.lan.util;

import android.os.Handler;
import android.os.Looper;

/** 统一的主线程调度，所有对外回调都经由这里切回主线程。 */
public final class MainThread {
    private static final Handler HANDLER = new Handler(Looper.getMainLooper());

    private MainThread() {
    }

    public static void post(Runnable r) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            r.run();
        } else {
            HANDLER.post(r);
        }
    }

    public static void postDelayed(Runnable r, long delayMs) {
        HANDLER.postDelayed(r, delayMs);
    }

    public static void remove(Runnable r) {
        HANDLER.removeCallbacks(r);
    }
}
