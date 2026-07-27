
package com.fongmi.android.tv.utils;

import android.os.Handler;
import android.os.Looper;

public class Timer {

    private static final Timer instance = new Timer();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable runnable;
    private boolean running;
    private long startTime;
    private long remainingMs;

    public static Timer get() { return instance; }

    public void start(Runnable task, long delayMs) {
        stop();
        remainingMs = delayMs;
        startTime = System.currentTimeMillis();
        runnable = () -> {
            running = false;
            task.run();
        };
        running = true;
        handler.postDelayed(runnable, delayMs);
    }

    public void stop() {
        running = false;
        if (runnable != null) handler.removeCallbacks(runnable);
        runnable = null;
    }

    public boolean isRunning() { return running; }

    public long getRemainingMs() {
        if (!running) return 0;
        return Math.max(0, remainingMs - (System.currentTimeMillis() - startTime));
    }

    public void pause() {
        if (!running) return;
        remainingMs = getRemainingMs();
        handler.removeCallbacks(runnable);
        running = false;
    }

    public void resume(Runnable task) {
        if (remainingMs <= 0) return;
        start(task, remainingMs);
    }
}
