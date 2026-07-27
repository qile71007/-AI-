
package com.fongmi.android.tv.service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import androidx.annotation.Nullable;

import org.jupnp.android.AndroidUpnpService;
import org.jupnp.android.AndroidUpnpServiceImpl;

public class DLNACastService extends AndroidUpnpServiceImpl {
    @Override
    public void onCreate() {
        super.onCreate();
        upnpService.startup();
    }

    @Override
    public void onDestroy() {
        upnpService.shutdown();
        super.onDestroy();
    }
}
