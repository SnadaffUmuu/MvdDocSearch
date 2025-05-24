package com.mvd.docsearchmvd.util;

import android.util.Log;

import java.util.Locale;

public class LogTimer {
    private long startTime;
    private long lastTime;
    private final String tag;
    private final boolean resetOnLog;
    private int recordCount = 0;
    private long accumulatedElapsed = 0;

    public LogTimer(String tag, boolean resetOnLog) {
        this.tag = tag;
        this.resetOnLog = resetOnLog;
        this.startTime = System.currentTimeMillis();
        this.lastTime = startTime;
    }

    public LogTimer(boolean resetOnLog) {
        this("LogTimer", resetOnLog);
    }

    private long getRecord() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastTime;
        recordCount ++;
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

    public void record(long elapsed) {
        accumulatedElapsed += elapsed;
        recordCount++;
    }

    public String getAverage() {
        if (recordCount == 0) return "n/a";
        return formatElapsed(getTotal() / recordCount);
    }

    public void reset() {
        this.startTime = System.currentTimeMillis();
        this.lastTime = this.startTime;
        this.recordCount = 0;
    }
}
