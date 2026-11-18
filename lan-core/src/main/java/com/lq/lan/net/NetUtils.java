package com.lq.lan.net;

import androidx.annotation.Nullable;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 网络工具。
 * <p>
 * 广播目标通过枚举所有网卡的定向广播地址，Wi-Fi / 热点 / 有线 / 不同网段都能覆盖，且不依赖隐藏 API。
 */
public final class NetUtils {

    public static final String LIMITED_BROADCAST = "255.255.255.255";

    private NetUtils() {
    }

    /** 本机所有非回环 IPv4 地址 */
    public static Set<String> localIpv4Addresses() {
        Set<String> result = new LinkedHashSet<>();
        for (NetworkInterface ni : interfaces()) {
            for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                InetAddress a = ia.getAddress();
                if (a instanceof Inet4Address && !a.isLoopbackAddress()) {
                    result.add(a.getHostAddress());
                }
            }
        }
        return result;
    }

    /**
     * 展示/组网用的本机地址：Wi-Fi、热点、有线网卡优先，其次才是其它私有网段地址。
     * 开着移动数据的手机上蜂窝网卡（rmnet/ccmni）往往也是 10.x 私有地址，不能按网段简单判断。
     */
    @Nullable
    public static String primaryIpv4() {
        String best = null;
        int bestRank = Integer.MAX_VALUE;
        for (NetworkInterface ni : interfaces()) {
            int rank = rankInterface(ni.getName());
            for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                InetAddress a = ia.getAddress();
                if (!(a instanceof Inet4Address) || a.isLoopbackAddress()) continue;
                String ip = a.getHostAddress();
                int r = rank * 2 + (isPrivate(ip) ? 0 : 1);
                if (r < bestRank) {
                    bestRank = r;
                    best = ip;
                }
            }
        }
        return best;
    }

    /** 数值越小越优先 */
    static int rankInterface(String name) {
        if (name == null) return 9;
        if (name.startsWith("wlan") || name.startsWith("swlan") || name.startsWith("ap")
                || name.startsWith("softap") || name.startsWith("eth") || name.startsWith("en")) return 0;
        if (name.startsWith("rmnet") || name.startsWith("ccmni") || name.startsWith("pdp")
                || name.startsWith("tun") || name.startsWith("ppp")) return 5;
        return 2;
    }

    /** 广播目标：受限广播 + 每个网卡的定向广播（去重） */
    public static Set<InetAddress> broadcastTargets() {
        Set<InetAddress> result = new LinkedHashSet<>();
        try {
            result.add(InetAddress.getByName(LIMITED_BROADCAST));
        } catch (UnknownHostException ignore) {
            // 不会发生
        }
        for (NetworkInterface ni : interfaces()) {
            for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                InetAddress a = ia.getAddress();
                if (!(a instanceof Inet4Address) || a.isLoopbackAddress()) continue;
                InetAddress bc = ia.getBroadcast();
                if (bc == null) bc = directedBroadcast((Inet4Address) a, ia.getNetworkPrefixLength());
                if (bc != null) result.add(bc);
            }
        }
        return result;
    }

    /** 根据地址和前缀长度计算定向广播地址，例如 192.168.1.23/24 -> 192.168.1.255 */
    @Nullable
    public static InetAddress directedBroadcast(Inet4Address addr, int prefixLength) {
        if (prefixLength <= 0 || prefixLength >= 32) return null;
        byte[] b = addr.getAddress();
        int ip = ((b[0] & 0xFF) << 24) | ((b[1] & 0xFF) << 16) | ((b[2] & 0xFF) << 8) | (b[3] & 0xFF);
        int mask = 0xFFFFFFFF << (32 - prefixLength);
        int bc = (ip & mask) | ~mask;
        byte[] out = {(byte) (bc >>> 24), (byte) (bc >>> 16), (byte) (bc >>> 8), (byte) bc};
        try {
            return InetAddress.getByAddress(out);
        } catch (UnknownHostException e) {
            return null;
        }
    }

    static boolean isPrivate(String ip) {
        return ip.startsWith("10.") || ip.startsWith("192.168.")
                || ip.matches("^172\\.(1[6-9]|2\\d|3[01])\\..*");
    }

    private static Iterable<NetworkInterface> interfaces() {
        try {
            java.util.List<NetworkInterface> list = new java.util.ArrayList<>();
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (ni.isUp() && !ni.isLoopback()) list.add(ni);
            }
            return list;
        } catch (SocketException | NullPointerException e) {
            return Collections.emptyList();
        }
    }
}
