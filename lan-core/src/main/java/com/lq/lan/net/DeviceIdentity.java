package com.lq.lan.net;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import java.util.UUID;

/** 本机在组网中的身份：安装级别的随机 ID（不涉及任何硬件标识）+ 设备型号 */
public final class DeviceIdentity {

    private static final String PREFS = "lan_core";
    private static final String KEY_ID = "device_id";

    public final String id;
    public final String name;

    private DeviceIdentity(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public static DeviceIdentity of(Context context) {
        SharedPreferences sp = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String id = sp.getString(KEY_ID, null);
        if (id == null) {
            id = UUID.randomUUID().toString();
            sp.edit().putString(KEY_ID, id).apply();
        }
        return new DeviceIdentity(id, Build.MODEL);
    }

    /** 测试用 */
    public static DeviceIdentity of(String id, String name) {
        return new DeviceIdentity(id, name);
    }
}
