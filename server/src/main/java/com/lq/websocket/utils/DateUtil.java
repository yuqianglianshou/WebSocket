package com.lq.websocket.utils;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @author : lq
 * @date : 2020/11/4
 * @desc :
 */
public class DateUtil {
    public static String getNowDate() {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy年MM月dd日 HH:mm:ss");// HH:mm:ss
        //获取当前时间
        Date date = new Date(System.currentTimeMillis());
        return simpleDateFormat.format(date);
    }

}
