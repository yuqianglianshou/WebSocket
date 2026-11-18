package com.lq.lan.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class TimeUtil {
    private TimeUtil() {
    }

    /** SimpleDateFormat 非线程安全，每次新建 */
    public static String now() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
    }
}
