package com.mvd.docsearchmvd.util;

import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class NativeLogger {

    private static final String LOG_TAG = "NativeLogger";
    private static final File logFile = new File("/storage/emulated/0/logs/search.log");

    public static void writeResultToFile(Object response) {
        String json = new com.google.gson.Gson().toJson(response);

        try {
            // Ensure parent directory exists
            File parent = logFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true))) {
                writer.write(json);
                writer.write("\n"); // one entry per line
            }

        } catch (IOException e) {
            Log.e(LOG_TAG, "Failed to write log to file", e);
        }
    }

    public static void resetLog() {
        if (logFile.exists()) {
            logFile.delete();
        }
    }
}
