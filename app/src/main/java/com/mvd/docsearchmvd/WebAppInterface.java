package com.mvd.docsearchmvd;

import static com.mvd.docsearchmvd.util.Util.escape;

import android.content.*;
import android.net.Uri;
import android.webkit.JavascriptInterface;
import android.util.Log;
import android.webkit.WebView;

import com.google.gson.reflect.TypeToken;
import com.mvd.docsearchmvd.db.DatabaseManager;
import com.mvd.docsearchmvd.indexer.FileIndexer;
import com.mvd.docsearchmvd.search.Hit;
import com.mvd.docsearchmvd.search.SearchEngine;

import com.google.gson.Gson;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.List;

public class WebAppInterface {
    public static final String TAG = "DocSearchMvdLog";
    private Context context;
    private WebView webView;
    private final Gson gson = new Gson();
    WebAppInterface(Context ctx, WebView webView) {
        this.context = ctx;
        this.webView = webView;
    }
    @JavascriptInterface
    public String doSearch(String query) {
        try {
            DatabaseManager db = new DatabaseManager(context);
            SearchEngine se = new SearchEngine(db);
            List<Hit> results = se.search(query);
            Log.d(TAG, "results num: " + results.size());
            String json = gson.toJson(results);
            Log.d(TAG, "results json: " + json);
            return json;
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            return "Произошла ошибка поиска<br>" + sw.toString();
        }
    }

    @JavascriptInterface
    public String getFileContent(String path) {
        try {
            return new String(Files.readAllBytes((new File(path)).toPath()), StandardCharsets.UTF_8);
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            return "Ошибка чтения файла" + path + "<br>" + sw.toString();
        }
      /*

BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), charset));
Deque<String> backLines = new LinkedList<>();
StringBuilder backBuffer = new StringBuilder();
List<String> results = new ArrayList<>();

String line;
while ((line = reader.readLine()) != null) {
    int index = line.indexOf(searchTerm);
    while (index != -1) {
        String before = backBuffer.length() > 100 ?
            backBuffer.substring(backBuffer.length() - 100) : backBuffer.toString();
        int afterEnd = Math.min(line.length(), index + searchTerm.length() + 100);
        String after = line.substring(index, afterEnd);
        results.add(before + after);
        index = line.indexOf(searchTerm, index + 1);
    }

    // Обновляем буфер
    backLines.add(line);
    backBuffer.append(line).append("\n");
    while (backBuffer.length() > 300) {
        String removed = backLines.removeFirst();
        backBuffer.delete(0, removed.length() + 1); // +1 из-за \n
    }
}

       */
    }

    @JavascriptInterface
    public String deleteIndexesForPaths (String jsonArrayString) {
        try {
            DatabaseManager db = new DatabaseManager(context);
            List<String> paths = gson.fromJson(jsonArrayString, new TypeToken<List<String>>(){}.getType());
            Log.d(WebAppInterface.TAG, "deleting indexes for paths: " + jsonArrayString);
            for (String path : paths) {
                db.deleteIndexForPath(path);
            }
            Log.d(WebAppInterface.TAG, "deleting indexes finished, returning folders...");
            return gson.toJson(db.getAllFolders());
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            Log.e(TAG, "Error deleting indexes for folders: " + sw.toString());
        }
        return null;
    }

    @JavascriptInterface
    public String clearDB() {
        try {
            DatabaseManager db = new DatabaseManager(context);
            db.clearTables();
            return gson.toJson(db.getAllFolders());
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            Log.e(TAG, "Error clearing DB: " + sw.toString());
        }
        return null;
    }

    @JavascriptInterface
    public void indexFolder(String path) {
        try {
            Log.d(TAG, "indexFolder called");
            File folder = new File(path);
            Log.d(TAG, "Path: " + path);
            Log.d(TAG, "Can read: " + folder.canRead());
            Log.d(TAG, "Exists: " + folder.exists());
            Log.d(TAG, "IsDirectory: " + folder.isDirectory());
            if (folder == null || !folder.exists() || !folder.isDirectory()) {
                Log.d(TAG, "Invalid folder");
                webView.post(() -> {
                    String message = "Невалидная папка";
                    String js = String.format("onIndexDone(%s)", JSONObject.quote(message));
                    webView.evaluateJavascript(js, null);
                });
            }

            DatabaseManager db = new DatabaseManager(context);
            FileIndexer fileIndexer = new FileIndexer(db, context);

            fileIndexer.setProgressListener((fileName, done, total, elapsedSeconds) -> {
                Log.d(TAG, "setProgressListenerCallback: done: " + done + ", total: " + total + ", elapsed: " + elapsedSeconds);
                int percent = (int)((done * 100.0) / total);
                Log.d(TAG, "setProgressListenerCallback: percent: " + percent);
                String js = String.format("onIndexProgress(\"%s\", %d, %d)", escape(fileName), percent, elapsedSeconds);
                Log.d(TAG, "setProgressListenerCallback: calling js: " + js);
                webView.post(() -> webView.evaluateJavascript(js, null));
            });

            // Запуск в отдельном потоке
            new Thread(() -> {
                File[] folders = new File[] { folder };
                try {
                    fileIndexer.updateIndex(folders);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
                String json = gson.toJson(db.getAllFolders());
                String escapedJson = JSONObject.quote(json);
                Log.d(TAG, "results json: " + escapedJson);
                webView.post(() -> {
                    String js = String.format("onIndexDone(%s)", escapedJson);
                    webView.evaluateJavascript(js, null);
                });
            }).start();

        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            webView.post(() -> {
                String js = String.format("onIndexDone(%s)", "Ошибка при выполнении индекса.");
                webView.evaluateJavascript(js, null);
            });
        }
    }

    @JavascriptInterface
    public void selectFolder() {
        ((MainActivity) context).selectFolder();
    }

    @JavascriptInterface
    public String getIndexedFolders() {
        DatabaseManager db = new DatabaseManager(context);
        return gson.toJson(db.getAllFolders());
    }

    @JavascriptInterface
    public void exportDB() {
        Log.d(TAG, "exportDB called");
        String dbName = "index.db";
        File dbFile = new File(context.getFilesDir(), "index.db");
        File exportDir = new File(context.getExternalFilesDir(null), "db-export");
        if (!exportDir.exists()) exportDir.mkdirs();

        File exportedFile = new File(exportDir, dbName);

        try (
                InputStream in = new FileInputStream(dbFile);
                OutputStream out = new FileOutputStream(exportedFile)
        ) {
            byte[] buffer = new byte[4096];
            int length;
            while ((length = in.read(buffer)) > 0) {
                out.write(buffer, 0, length);
            }
            out.flush();
            Log.d(TAG, "export completed");
        } catch (IOException e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            Log.e(WebAppInterface.TAG, sw.toString());
        }
    }

    private Uri getFolderUri() {
        SharedPreferences prefs = context.getSharedPreferences("docsearchmvd_prefs", Context.MODE_PRIVATE);
        String uriString = prefs.getString("folder_uri", null);
        return uriString != null ? Uri.parse(uriString) : null;
    }
}
