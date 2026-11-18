package com.lq.lan.ui;

import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

import androidx.activity.ComponentActivity;
import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Locale;

/** 两个演示 App 共用的界面工具 */
public final class LanUi {

    private LanUi() {
    }

    /**
     * targetSdk 35 起系统强制全面屏（edge-to-edge），内容会画到状态栏/导航栏下面。
     * 这里统一开启并把系统栏、软键盘占用的区域作为 padding 让出来。
     */
    public static void setupEdgeToEdge(ComponentActivity activity, View root) {
        EdgeToEdge.enable(activity);
        final int l = root.getPaddingLeft(), t = root.getPaddingTop(), r = root.getPaddingRight(), b = root.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(l + bars.left, t + bars.top, r + bars.right, b + Math.max(bars.bottom, ime.bottom));
            return WindowInsetsCompat.CONSUMED;
        });
    }

    /** 初始化消息列表，新消息到来时若用户停留在底部则自动滚动 */
    public static LogAdapter setupLogList(RecyclerView rv) {
        LinearLayoutManager lm = new LinearLayoutManager(rv.getContext());
        lm.setStackFromEnd(true);
        rv.setLayoutManager(lm);
        rv.setItemAnimator(null);
        LogAdapter adapter = new LogAdapter();
        rv.setAdapter(adapter);
        return adapter;
    }

    public static void submitLog(RecyclerView rv, LogAdapter adapter, List<LogEntry> list) {
        boolean atBottom = !rv.canScrollVertically(1);
        adapter.submitList(list, () -> {
            if (atBottom && !list.isEmpty()) rv.scrollToPosition(list.size() - 1);
        });
    }

    /** 软键盘上的「发送」键 */
    public static void onImeSend(EditText et, Runnable action) {
        et.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                action.run();
                return true;
            }
            return false;
        });
    }

    public static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.US, "%.1f KB", bytes / 1024f);
        return String.format(Locale.US, "%.1f MB", bytes / 1024f / 1024f);
    }
}
