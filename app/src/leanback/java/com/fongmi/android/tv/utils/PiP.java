
package com.fongmi.android.tv.utils;

import android.app.Activity;
import android.app.PictureInPictureParams;
import android.content.res.Configuration;
import android.os.Build;
import android.util.Rational;

public class PiP {

    public static void enter(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                Rational aspectRatio = new Rational(16, 9);
                activity.enterPictureInPictureMode(new PictureInPictureParams.Builder()
                        .setAspectRatio(aspectRatio)
                        .build());
            } catch (Exception ignored) { }
        }
    }

    public static boolean isSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
    }

    public static boolean isInPiP(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return activity.isInPictureInPictureMode();
        }
        return false;
    }

    public static void onConfigurationChanged(Activity activity, Configuration newConfig) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (newConfig.screenWidthDp < newConfig.screenHeightDp) {
                // PiP mode
            }
        }
    }
}
