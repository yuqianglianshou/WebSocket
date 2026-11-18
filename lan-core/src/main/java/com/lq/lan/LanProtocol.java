package com.lq.lan;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;

/**
 * WebSocket 握手阶段的约定。
 * <p>
 * 从机在握手请求头里带上设备标识与分组；主机据此识别设备、拒绝其它分组。
 * 这些头都是可选的：缺失时（例如 1.0 版本的从机）主机退化为按 IP 识别，保持兼容。
 */
public final class LanProtocol {

    public static final String HEADER_DEVICE_ID = "X-Lan-Device-Id";
    /** 设备名可能含中文，HTTP 头只允许 ASCII，统一 URL 编码 */
    public static final String HEADER_DEVICE_NAME = "X-Lan-Device-Name";
    public static final String HEADER_TEAM = "X-Lan-Team";
    public static final String HEADER_TASK = "X-Lan-Task";

    /** 主机已满（RFC 6455 Try Again Later） */
    public static final int CLOSE_SERVER_FULL = 1013;
    /** 被同一设备的新连接顶替 */
    public static final int CLOSE_REPLACED = 4001;

    private LanProtocol() {
    }

    public static String encode(String v) {
        try {
            return URLEncoder.encode(v == null ? "" : v, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String decode(String v) {
        if (v == null) return "";
        try {
            return URLDecoder.decode(v, "UTF-8");
        } catch (UnsupportedEncodingException | IllegalArgumentException e) {
            return v;
        }
    }
}
