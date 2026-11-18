package com.lq.lan.ui;

/** 状态卡片的展示模型 */
public final class StatusModel {

    public enum Level {IDLE, WORKING, OK, WARN, ERROR}

    public final Level level;
    public final String title;
    public final String detail;
    /**
     * 是否显示进度条动画。只用于几秒内就会结束的过程（如正在连接）：
     * 「等待学生加入」「搜索中」可能持续整节课，无限动画会让 RenderThread 一直工作、持续耗电。
     */
    public final boolean busy;

    public StatusModel(Level level, String title, String detail) {
        this(level, title, detail, false);
    }

    public StatusModel(Level level, String title, String detail, boolean busy) {
        this.level = level;
        this.title = title;
        this.detail = detail;
        this.busy = busy;
    }
}
