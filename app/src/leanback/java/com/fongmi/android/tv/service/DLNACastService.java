package com.fongmi.android.tv.service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import androidx.annotation.Nullable;
import com.fongmi.android.tv.bean.CastVideo;
import com.fongmi.android.tv.dlna.DLNACastManager;

public class DLNACastService extends Service {
    @Nullable @Override
    public IBinder onBind(Intent intent) { return null; }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        DLNACastManager.get().setCasting(true);
        return START_STICKY;
    }
    @Override
    public void onDestroy() {
        DLNACastManager.get().setCasting(false);
        super.onDestroy();
    }
}
