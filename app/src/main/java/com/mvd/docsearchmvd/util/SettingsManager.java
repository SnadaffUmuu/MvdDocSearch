package com.mvd.docsearchmvd.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

public class SettingsManager {
    private static final String PREFS_NAME = "app_settings";
    private static final String KEY_ALLOWED_EXTENSIONS = "allowed_extensions";
    private static final String KEY_EXCLUDED_PATHS = "excluded_paths";

    private SharedPreferences preferences;

    public SettingsManager(Context context) {
        preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void saveAllowedExtensions(String extensionsCsv) {
        saveSetting(KEY_ALLOWED_EXTENSIONS, extensionsCsv);
    }

    public void saveExcludedPaths(String extensionsCsv) {
        saveSetting(KEY_EXCLUDED_PATHS, extensionsCsv);
    }

    public Set<String> getAllowedExtensions() {
        return getSettingsValue(KEY_ALLOWED_EXTENSIONS);
    }

    public Set<String> getExcludedPaths() {
        return getSettingsValue(KEY_EXCLUDED_PATHS);
    }

    public String getAllowedExtensionsRaw() {
        return preferences.getString(KEY_ALLOWED_EXTENSIONS, "");
    }

    public String getExcludedPathsRaw() {
        return preferences.getString(KEY_EXCLUDED_PATHS, "");
    }

    private void saveSetting(String key, String value) {
        preferences.edit().putString(key, value).apply();
    }

    private Set<String> getSettingsValue(String key) {
        String csv = preferences.getString(key, "");
        Set<String> result = new HashSet<>();
        for (String path : csv.split(",")) {
            path = path.trim().toLowerCase();
            if (!path.isEmpty()) {
                result.add(path);
            }
        }
        return result;
    }
}

