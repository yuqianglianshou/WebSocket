package com.lq.client;

import android.app.Application;


public class MyApplication extends Application {

    public static MyApplication app = null;

    @Override
    public void onCreate() {
        super.onCreate();
        app = this;
    }
}
