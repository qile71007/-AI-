package com.fongmi.android.tv.utils;

import android.app.Activity;
import android.app.PictureInPictureParams;
import android.os.Build;

public class PiP {
    public static void enter(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity.enterPictureInPictureMode(new PictureInPictureParams.Builder().build());
        }
    }
    public static boolean isSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
    }
}
