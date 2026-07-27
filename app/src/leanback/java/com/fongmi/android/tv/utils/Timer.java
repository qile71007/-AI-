package com.fongmi.android.tv.utils;

import android.os.Handler;
import android.os.Looper;

public class Timer {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable runnable;
    private boolean running;

    public void start(Runnable task, long intervalMs) {
        runnable = () -> { task.run(); if (running) handler.postDelayed(runnable, intervalMs); };
        running = true;
        handler.post(runnable);
    }
    public void stop() { running = false; handler.removeCallbacks(runnable); }
    public boolean isRunning() { return running; }
}
