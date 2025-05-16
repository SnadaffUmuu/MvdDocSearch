package com.mvd.docsearchmvd.util;

import java.io.PrintWriter;
import java.io.StringWriter;

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
}
