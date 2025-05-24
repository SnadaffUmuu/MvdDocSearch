package com.mvd.docsearchmvd.util;

import android.webkit.WebView;

import com.mvd.docsearchmvd.WebAppInterface;

import org.json.JSONObject;

import java.io.PrintWriter;
import java.io.StringWriter;
import android.util.Log;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class Util {
    public static boolean isAsciiLetterOrCyrillicOrDigit(char ch) {
        return (ch >= 'a' && ch <= 'z') ||
                (ch >= 'A' && ch <= 'Z') ||
                (ch >= '0' && ch <= '9') ||
                (ch >= '\u0400' && ch <= '\u04FF');
    }

    public static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public static String getStackTrace(Exception e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    public static void sendResultToJS(WebView webView, Object response) {
        String json = new com.google.gson.Gson().toJson(response);
        Log.d(WebAppInterface.TAG, "sendResultToJS: " + json);
        String jsCode = "window._onNativeMessage && window._onNativeMessage("
                + JSONObject.quote(json) + ");";
        webView.post(() -> webView.evaluateJavascript(jsCode, null));
    }

    public static String formatUnitTime(Long time) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        LocalDateTime dateTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(time),
                ZoneId.systemDefault()
        );
        return dateTime.format(formatter);
    }

    public static String formatElapsed(long ms) {
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

    public static String formatUnixTime(long ms) {
        return Instant.ofEpochMilli(ms)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
    }

    public static List<Integer> mergeSortedLists(List<Integer> a, List<Integer> b) {
        List<Integer> result = new ArrayList<>(a.size() + b.size());
        int i = 0, j = 0;
        while (i < a.size() && j < b.size()) {
            if (a.get(i) <= b.get(j)) {
                result.add(a.get(i++));
            } else {
                result.add(b.get(j++));
            }
        }
        while (i < a.size()) result.add(a.get(i++));
        while (j < b.size()) result.add(b.get(j++));
        return result;
    }
}
