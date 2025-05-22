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
}
