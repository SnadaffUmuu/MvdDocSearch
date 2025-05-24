package com.mvd.docsearchmvd.util;

import java.util.HashMap;
import java.util.Map;

public class Profiler {
    private static final Map<String, LogTimer> timers = new HashMap<>();

    public static LogTimer get(String key) {
        return timers.computeIfAbsent(key, k -> new LogTimer(key,false));
    }

    public static void resetAll() {
        for (LogTimer timer : timers.values()) {
            timer.reset();
        }
    }

    public static void clear() {
        timers.clear();
    }

    public static String getTimers() {
        return String.valueOf(timers);
    }
}