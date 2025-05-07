package com.mvd.docsearchmvd.util;
public class Util {
    public static boolean isAsciiLetterOrCyrillicOrDigit(char ch) {
        return (ch >= 'a' && ch <= 'z') ||
                (ch >= 'A' && ch <= 'Z') ||
                (ch >= '0' && ch <= '9') ||
                (ch >= '\u0400' && ch <= '\u04FF');
    }
}
