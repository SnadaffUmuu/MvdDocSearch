package com.mvd.docsearchmvd.util;

import android.util.Log;

import static com.mvd.docsearchmvd.util.Util.formatElapsed;
import static com.mvd.docsearchmvd.util.Util.formatUnixTime;

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
        if (resetOnLog) {
            lastTime = now;
        }
        return elapsed;
    }

    public String getElapsed () {
        return formatElapsed(getRecord());
    }

    private long getTotal () {
        long now = System.currentTimeMillis();
        return now - startTime;
    }

    public void logTotal(String message) {
        Log.d(tag, message + " (TOTAL: " + formatElapsed(getTotal()) + ")");
    }

    public void record(long elapsed) {
        accumulatedElapsed += elapsed;
        recordCount++;
    }

    public String getAverage() {
        if (recordCount == 0) return "n/a";
        return formatElapsed(accumulatedElapsed / recordCount);
    }

    public void reset() {
        this.startTime = System.currentTimeMillis();
        this.lastTime = this.startTime;
        this.recordCount = 0;
    }

    public String toString() {
        return "{\n" +
            "tag='" + tag +
            "\nresetOnLog=" + resetOnLog +
            "\nstartTime=" + formatUnixTime(startTime) +
            "\nlastTime=" + formatUnixTime(lastTime) +
            "\nrecordCount=" + recordCount +
            "\naccumulatedElapsed=" + formatElapsed(accumulatedElapsed) +
            "\n}";
    }
}
