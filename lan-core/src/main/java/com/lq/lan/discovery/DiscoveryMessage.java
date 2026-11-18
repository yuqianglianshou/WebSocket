package com.lq.lan.discovery;

import androidx.annotation.Nullable;

import com.lq.lan.LanConfig;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * UDP 发现包协议，JSON 格式，字段与 1.0 版本兼容：
 * <pre>
 * {"type":"master|slave","teamId":"..","taskId":"..","deviceName":"..","port":8880}
 * </pre>
 */
public final class DiscoveryMessage {

    public enum Role {
        MASTER("master"), SLAVE("slave");

        final String wire;

        Role(String wire) {
            this.wire = wire;
        }

        @Nullable
        static Role of(String wire) {
            for (Role r : values()) {
                if (r.wire.equals(wire)) return r;
            }
            return null;
        }
    }

    public final Role role;
    public final String teamId;
    public final String taskId;
    public final String deviceName;
    /** 仅主机包携带：WebSocket 服务端口，<=0 表示未携带 */
    public final int wsPort;

    public DiscoveryMessage(Role role, String teamId, String taskId, String deviceName, int wsPort) {
        this.role = role;
        this.teamId = teamId;
        this.taskId = taskId;
        this.deviceName = deviceName == null ? "" : deviceName;
        this.wsPort = wsPort;
    }

    public static DiscoveryMessage master(LanConfig c, String deviceName) {
        return new DiscoveryMessage(Role.MASTER, c.teamId, c.taskId, deviceName, c.wsPort);
    }

    public static DiscoveryMessage slave(LanConfig c, String deviceName) {
        return new DiscoveryMessage(Role.SLAVE, c.teamId, c.taskId, deviceName, 0);
    }

    /** 是否属于同一组（team + task 都相同） */
    public boolean sameGroup(LanConfig c) {
        return c.teamId.equals(teamId) && c.taskId.equals(taskId);
    }

    public String toJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("type", role.wire);
            o.put("teamId", teamId);
            o.put("taskId", taskId);
            o.put("deviceName", deviceName);
            if (wsPort > 0) o.put("port", wsPort);
            return o.toString();
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 解析失败或字段不全时返回 null（局域网里什么包都可能收到，不能抛异常） */
    @Nullable
    public static DiscoveryMessage parse(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        try {
            JSONObject o = new JSONObject(raw);
            Role role = Role.of(o.optString("type"));
            String team = o.optString("teamId");
            String task = o.optString("taskId");
            if (role == null || team.isEmpty() || task.isEmpty()) return null;
            return new DiscoveryMessage(role, team, task, o.optString("deviceName"), o.optInt("port", 0));
        } catch (JSONException e) {
            return null;
        }
    }

    @Override
    public String toString() {
        return toJson();
    }
}
