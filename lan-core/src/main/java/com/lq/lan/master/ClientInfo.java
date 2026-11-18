package com.lq.lan.master;

import androidx.annotation.NonNull;

/** 已连接的从机。id 优先取握手头里的设备 ID，1.0 从机没有该头时退化为 IP */
public final class ClientInfo {

    public final String id;
    public final String ip;
    public final String name;

    ClientInfo(String id, String ip, String name) {
        this.id = id;
        this.ip = ip;
        this.name = name == null || name.isEmpty() ? ip : name;
    }

    /** 展示用：「设备名 (IP)」 */
    @NonNull
    public String label() {
        return name.equals(ip) ? ip : name + " (" + ip + ")";
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ClientInfo && ((ClientInfo) o).id.equals(id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @NonNull
    @Override
    public String toString() {
        return label();
    }
}
