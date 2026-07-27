package com.fongmi.android.tv.ui.custom;

import android.view.KeyEvent;
import android.view.MotionEvent;

public class CustomKeyDown {
    public boolean onTouchEvent(MotionEvent event) { return false; }
    public boolean dispatchKeyEvent(KeyEvent event) { return false; }
}
