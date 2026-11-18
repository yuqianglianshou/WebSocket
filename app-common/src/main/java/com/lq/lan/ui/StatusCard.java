package com.lq.lan.ui;

import android.content.res.ColorStateList;
import android.text.TextUtils;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.core.content.ContextCompat;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.progressindicator.LinearProgressIndicator;

/** 绑定 lan_status_card 布局：状态点 + 标题 + 详情 + 开关 + 进行中进度条 */
public final class StatusCard {

    public interface ToggleListener {
        void onToggle(boolean on);
    }

    private final View dot;
    private final TextView title;
    private final TextView detail;
    private final MaterialSwitch toggle;
    private final LinearProgressIndicator progress;
    private CompoundButton.OnCheckedChangeListener toggleListener;

    public StatusCard(View root) {
        dot = root.findViewById(R.id.lan_status_dot);
        title = root.findViewById(R.id.lan_status_title);
        detail = root.findViewById(R.id.lan_status_detail);
        toggle = root.findViewById(R.id.lan_status_toggle);
        // 开关状态只来自 ViewModel：若让 View 自行恢复，进程被杀后重开页面会在 onRestoreInstanceState 里
        // 触发监听器，悄悄重新开始组网
        toggle.setSaveEnabled(false);
        progress = root.findViewById(R.id.lan_status_progress);
    }

    public void setToggleText(CharSequence text) {
        toggle.setText(text);
    }

    public void setOnToggleListener(ToggleListener l) {
        toggleListener = (b, checked) -> l.onToggle(checked);
        toggle.setOnCheckedChangeListener(toggleListener);
    }

    /** 由数据驱动开关状态，不触发监听，避免「回调里又 start/stop」的循环 */
    public void setToggleChecked(boolean checked) {
        if (toggle.isChecked() == checked) return;
        toggle.setOnCheckedChangeListener(null);
        toggle.setChecked(checked);
        toggle.setOnCheckedChangeListener(toggleListener);
    }

    public void render(StatusModel m) {
        title.setText(m.title);
        detail.setText(m.detail);
        detail.setVisibility(TextUtils.isEmpty(m.detail) ? View.GONE : View.VISIBLE);
        dot.setBackgroundTintList(ColorStateList.valueOf(colorOf(m.level)));
        // INVISIBLE 时进度条会停止动画；GONE 会导致卡片高度跳动
        progress.setVisibility(m.busy ? View.VISIBLE : View.INVISIBLE);
    }

    @ColorInt
    private int colorOf(StatusModel.Level level) {
        int res;
        switch (level) {
            case OK:
                res = R.color.lan_status_ok;
                break;
            case WORKING:
                res = R.color.lan_status_working;
                break;
            case WARN:
                res = R.color.lan_status_warn;
                break;
            case ERROR:
                res = R.color.lan_status_error;
                break;
            default:
                res = R.color.lan_status_idle;
        }
        return ContextCompat.getColor(dot.getContext(), res);
    }
}
