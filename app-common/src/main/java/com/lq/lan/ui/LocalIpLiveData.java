package com.lq.lan.ui;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkRequest;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;

import com.lq.lan.net.NetUtils;

/** 本机 IP，网络切换（换 Wi-Fi、开关热点）时自动刷新；无网络时为 null */
public final class LocalIpLiveData extends LiveData<String> {

    private final ConnectivityManager cm;
    private final ConnectivityManager.NetworkCallback callback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(@NonNull Network network) {
            refresh();
        }

        @Override
        public void onLost(@NonNull Network network) {
            refresh();
        }

        @Override
        public void onLinkPropertiesChanged(@NonNull Network network, @NonNull LinkProperties lp) {
            refresh();
        }
    };

    public LocalIpLiveData(Context context) {
        cm = (ConnectivityManager) context.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    private void refresh() {
        String ip = NetUtils.primaryIpv4();
        String old = getValue();
        if (ip == null ? old != null : !ip.equals(old)) postValue(ip);
    }

    @Override
    protected void onActive() {
        setValue(NetUtils.primaryIpv4());
        if (cm != null) cm.registerNetworkCallback(new NetworkRequest.Builder().build(), callback);
    }

    @Override
    protected void onInactive() {
        if (cm != null) cm.unregisterNetworkCallback(callback);
    }
}
