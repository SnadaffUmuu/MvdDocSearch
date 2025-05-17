package com.mvd.docsearchmvd.util;

import android.util.Log;

public class LogTimer {
    private long startTime;
    private long lastTime;
    private final String tag;
    private final boolean resetOnLog;

    public LogTimer(String tag, boolean resetOnLog) {
        this.tag = tag;
        this.resetOnLog = resetOnLog;
        this.startTime = System.currentTimeMillis();
        this.lastTime = startTime;
    }

    public void log(String message) {
        long now = System.currentTimeMillis();
        long elapsed = now - lastTime;
        if (resetOnLog) {
            lastTime = now;
        }
        Log.d(tag, message + " (+" + formatElapsed(elapsed) + ")");
    }

    public void logTotal(String message) {
        long now = System.currentTimeMillis();
        long totalElapsed = now - startTime;
        Log.d(tag, message + " (TOTAL: " + formatElapsed(totalElapsed) + ")");
    }

    private String formatElapsed(long ms) {
        return (ms < 1000) ? ms + " ms" : String.format("%.2f s", ms / 1000.0);
    }

    public void reset() {
        this.startTime = System.currentTimeMillis();
        this.lastTime = this.startTime;
    }
}
