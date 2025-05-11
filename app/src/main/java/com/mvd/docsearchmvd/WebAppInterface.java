package com.mvd.docsearchmvd;

import android.content.*;
import android.net.Uri;
import android.webkit.JavascriptInterface;
import android.util.Log;

import com.mvd.docsearchmvd.db.DatabaseManager;
import com.mvd.docsearchmvd.indexer.FileIndexer;
import com.mvd.docsearchmvd.search.Hit;
import com.mvd.docsearchmvd.search.SearchEngine;

import com.google.gson.Gson;

import java.io.File;
import java.io.StringWriter;
import java.io.PrintWriter;
import java.util.List;

public class WebAppInterface {
    public static final String TAG = "DocSearchMvdLog";
    private Context context;
    WebAppInterface(Context ctx) {
        this.context = ctx;
    }
    @JavascriptInterface
    public String doSearch(String query) {
        try {
            Log.d(TAG, "doSearch called");
            DatabaseManager db = new DatabaseManager(context);
            SearchEngine se = new SearchEngine(db);
            List<Hit> results = se.search(query);
            Log.d(TAG, "results num: " + results.size());
            Gson gson = new Gson();
            String json = gson.toJson(results);
            Log.d(TAG, "results json: " + json);
            return json;
        } catch (Exception e) {
            return "Произошла ошибка поиска";
        }
    }

    @JavascriptInterface
    public String doIndex(String path) {
        try {
            Log.d(TAG, "doIndex called");
            File folder = new File(path);
            Log.d(TAG, "Path: " + path);
            Log.d(TAG, "Can read: " + folder.canRead());
            Log.d(TAG, "Exists: " + folder.exists());
            Log.d(TAG, "IsDirectory: " + folder.isDirectory());
            if (folder == null || !folder.exists() || !folder.isDirectory()) return "Invalid folder.";

            DatabaseManager db = new DatabaseManager(context);
            db.init(true);

            FileIndexer fileIndexer = new FileIndexer(db, context);

            File[] folders = new File[] { folder };
            fileIndexer.updateIndex(folders);

            return "Индекс " + path + "успешно завершен";
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            return "Ошибка при выполнении индекса.<br>" + sw.toString();
        }
    }

    @JavascriptInterface
    public void selectFolder() {
        ((MainActivity) context).selectFolder();
    }

    private Uri getFolderUri() {
        SharedPreferences prefs = context.getSharedPreferences("docsearchmvd_prefs", Context.MODE_PRIVATE);
        String uriString = prefs.getString("folder_uri", null);
        return uriString != null ? Uri.parse(uriString) : null;
    }
}
