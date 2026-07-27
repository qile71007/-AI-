package com.fongmi.android.tv.dlna;

import android.content.Context;
import android.content.Intent;
import com.fongmi.android.tv.bean.CastVideo;
import com.fongmi.android.tv.service.DLNACastService;

public class DLNACast {
    public static void start(Context context, CastVideo video) {
        Intent intent = new Intent(context, DLNACastService.class);
        intent.putExtra("video", video);
        context.startService(intent);
    }
    public static void stop(Context context) {
        context.stopService(new Intent(context, DLNACastService.class));
    }
}
