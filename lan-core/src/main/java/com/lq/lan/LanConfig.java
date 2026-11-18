package com.lq.lan;

/**
 * 局域网组网的全部可调参数。
 * <p>
 * 主机端与从机端必须使用相同的 teamId / taskId / 端口 / 子协议，否则互相发现不了。
 * 默认值与 1.0 版本保持一致，新旧版本可以互通。
 */
public final class LanConfig {

    /** 分组标识：同一局域网内可以有多组主从机互不干扰 */
    public final String teamId;
    public final String taskId;

    /** 主机 WebSocket 服务端口 */
    public final int wsPort;
    /** WebSocket 子协议 */
    public final String wsProtocol;

    /** 主机监听 UDP 的端口（从机往这里回包） */
    public final int masterListenPort;
    /** 从机监听 UDP 的端口（主机往这里广播） */
    public final int slaveListenPort;

    /** 主机广播间隔 */
    public final long broadcastIntervalMs;
    /** 主机允许的最大从机数 */
    public final int maxSlaves;

    /** 从机 ping 间隔：OkHttp 在下一次 ping 前未收到 pong 即判定断线 */
    public final long heartbeatIntervalMs;
    /** 主机侧死连接检测：超过该时长无 pong 的连接由服务端断开 */
    public final long heartbeatTimeoutMs;
    /** 从机断线后重新进入搜索的延迟 */
    public final long reconnectDelayMs;

    private LanConfig(Builder b) {
        teamId = b.teamId;
        taskId = b.taskId;
        wsPort = b.wsPort;
        wsProtocol = b.wsProtocol;
        masterListenPort = b.masterListenPort;
        slaveListenPort = b.slaveListenPort;
        broadcastIntervalMs = b.broadcastIntervalMs;
        maxSlaves = b.maxSlaves;
        heartbeatIntervalMs = b.heartbeatIntervalMs;
        heartbeatTimeoutMs = b.heartbeatTimeoutMs;
        reconnectDelayMs = b.reconnectDelayMs;
    }

    public static LanConfig defaults() {
        return new Builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String teamId = "Speakin";
        private String taskId = "Test";
        private int wsPort = 8880;
        private String wsProtocol = "speakinchat";
        private int masterListenPort = 8882;
        private int slaveListenPort = 8883;
        private long broadcastIntervalMs = 5_000;
        private int maxSlaves = 80;
        private long heartbeatIntervalMs = 10_000;
        private long heartbeatTimeoutMs = 30_000;
        private long reconnectDelayMs = 2_000;

        public Builder teamId(String v) { teamId = v; return this; }
        public Builder taskId(String v) { taskId = v; return this; }
        public Builder wsPort(int v) { wsPort = v; return this; }
        public Builder wsProtocol(String v) { wsProtocol = v; return this; }
        public Builder masterListenPort(int v) { masterListenPort = v; return this; }
        public Builder slaveListenPort(int v) { slaveListenPort = v; return this; }
        public Builder broadcastIntervalMs(long v) { broadcastIntervalMs = v; return this; }
        public Builder maxSlaves(int v) { maxSlaves = v; return this; }
        public Builder heartbeatIntervalMs(long v) { heartbeatIntervalMs = v; return this; }
        public Builder heartbeatTimeoutMs(long v) { heartbeatTimeoutMs = v; return this; }
        public Builder reconnectDelayMs(long v) { reconnectDelayMs = v; return this; }

        public LanConfig build() {
            if (teamId == null || teamId.isEmpty() || taskId == null || taskId.isEmpty()) {
                throw new IllegalArgumentException("teamId/taskId must not be empty");
            }
            if (maxSlaves <= 0) throw new IllegalArgumentException("maxSlaves must be > 0");
            if (heartbeatTimeoutMs <= heartbeatIntervalMs) {
                throw new IllegalArgumentException("heartbeatTimeoutMs must be > heartbeatIntervalMs");
            }
            return new LanConfig(this);
        }
    }
}
