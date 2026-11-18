package com.lq.lan.ui;

import java.util.concurrent.atomic.AtomicLong;

/** 消息列表中的一条记录，不可变 */
public final class LogEntry {

    public enum Type {
        /** 系统事件：连接、断开、状态变化 */
        SYSTEM,
        /** 错误事件 */
        ERROR,
        /** 收到的消息 */
        IN,
        /** 发出的消息 */
        OUT
    }

    private static final AtomicLong SEQ = new AtomicLong();

    public final long id = SEQ.incrementAndGet();
    public final long time = System.currentTimeMillis();
    public final Type type;
    /** 气泡上方的小标题（发送方/接收方），SYSTEM/ERROR 可为空 */
    public final String title;
    public final String text;

    private LogEntry(Type type, String title, String text) {
        this.type = type;
        this.title = title;
        this.text = text;
    }

    public static LogEntry system(String text) {
        return new LogEntry(Type.SYSTEM, null, text);
    }

    public static LogEntry error(String text) {
        return new LogEntry(Type.ERROR, null, text);
    }

    public static LogEntry in(String from, String text) {
        return new LogEntry(Type.IN, from, text);
    }

    public static LogEntry out(String to, String text) {
        return new LogEntry(Type.OUT, to, text);
    }
}
