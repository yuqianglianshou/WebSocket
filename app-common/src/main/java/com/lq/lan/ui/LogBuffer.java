package com.lq.lan.ui;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 有上限的消息缓存，放在 ViewModel 里，旋转屏幕不丢；只在主线程调用 */
public final class LogBuffer {

    private final int capacity;
    private final ArrayDeque<LogEntry> items;
    private final MutableLiveData<List<LogEntry>> live = new MutableLiveData<>(Collections.<LogEntry>emptyList());

    public LogBuffer(int capacity) {
        this.capacity = capacity;
        this.items = new ArrayDeque<>(capacity);
    }

    public void add(LogEntry e) {
        if (items.size() == capacity) items.removeFirst();
        items.addLast(e);
        live.setValue(Collections.unmodifiableList(new ArrayList<>(items)));
    }

    public void clear() {
        items.clear();
        live.setValue(Collections.<LogEntry>emptyList());
    }

    public LiveData<List<LogEntry>> asLiveData() {
        return live;
    }
}
