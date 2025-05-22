package com.mvd.docsearchmvd.util;

import android.util.Log;

import java.util.Locale;

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

    public LogTimer(boolean resetOnLog) {
        this.tag = "LogTimer";
        this.resetOnLog = resetOnLog;
        this.startTime = System.currentTimeMillis();
        this.lastTime = startTime;
    }

    private long getRecord() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastTime;
        if (resetOnLog) {
            lastTime = now;
        }
        return elapsed;
    }

    public String getElapsed () {
        return formatElapsed(getRecord());
    }

    public void logElapsed(String message) {
        Log.d(tag, message + " (" + formatElapsed(getRecord()) + ")");
    }

    private long getTotal () {
        long now = System.currentTimeMillis();
        return now - startTime;
    }

    public String getTotalElapsed () {
        return formatElapsed(getTotal());
    }

    public void logTotal(String message) {
        Log.d(tag, message + " (TOTAL: " + formatElapsed(getTotal()) + ")");
    }

    private String formatElapsed(long ms) {
        if (ms < 1000) {
            return ms + " ms";
        } else if (ms < 60000) {
            double seconds = ms / 1000.0;
            double roundedUp = Math.ceil(seconds * 10) / 10.0;
            return String.format(Locale.US, "%.1f s", roundedUp);
        } else {
            double minutes = ms / 60000.0;
            double roundedUp = Math.ceil(minutes * 10) / 10.0;
            return String.format(Locale.US, "%.1f m", roundedUp);
        }
    }

    public void reset() {
        this.startTime = System.currentTimeMillis();
        this.lastTime = this.startTime;
    }
}
