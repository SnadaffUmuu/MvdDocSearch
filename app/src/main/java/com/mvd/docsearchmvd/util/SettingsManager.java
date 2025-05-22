package com.mvd.docsearchmvd.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

public class SettingsManager {
    private static final String PREFS_NAME = "app_settings";
    private static final String KEY_ALLOWED_EXTENSIONS = "allowed_extensions";

    private SharedPreferences preferences;

    public SettingsManager(Context context) {
        preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

        public void saveAllowedExtensions(String extensionsCsv) {
        preferences.edit().putString(KEY_ALLOWED_EXTENSIONS, extensionsCsv).apply();
    }

    public Set<String> getAllowedExtensions() {
        String csv = preferences.getString(KEY_ALLOWED_EXTENSIONS, "");
        Set<String> result = new HashSet<>();
        for (String ext : csv.split(",")) {
            ext = ext.trim().toLowerCase();
            if (!ext.isEmpty()) {
                result.add(ext);
            }
        }
        return result;
    }

    public String getAllowedExtensionsRaw() {
        return preferences.getString(KEY_ALLOWED_EXTENSIONS, "");
    }

    public boolean isExtensionAllowed(String fileName) {
        Set<String> allowed = getAllowedExtensions();
        String ext = getFileExtension(fileName);
        return allowed.contains(ext.toLowerCase());
    }

    private String getFileExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        return (lastDot != -1 && lastDot < fileName.length() - 1)
                ? fileName.substring(lastDot + 1).toLowerCase()
                : "";
    }
}

