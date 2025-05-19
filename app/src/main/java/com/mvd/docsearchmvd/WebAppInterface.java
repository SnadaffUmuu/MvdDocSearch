package com.mvd.docsearchmvd;

import static com.mvd.docsearchmvd.util.Util.getStackTrace;
import static com.mvd.docsearchmvd.util.Util.sendResultToJS;

import android.content.*;
import android.webkit.JavascriptInterface;
import android.util.Log;
import android.webkit.WebView;

import com.google.gson.reflect.TypeToken;
import com.mvd.docsearchmvd.db.DatabaseManager;
import com.mvd.docsearchmvd.indexer.FileIndexer;
import com.mvd.docsearchmvd.model.Hit;
import com.mvd.docsearchmvd.search.SearchEngine;

import com.google.gson.Gson;

import com.mvd.docsearchmvd.model.ApiResponse;
import com.mvd.docsearchmvd.util.LogTimer;

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
import java.util.Map;

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
            Log.d(TAG, "back: doSearch called, query: " + query);
            DatabaseManager db = ((MainActivity) context).getDbManager();
            Log.d(TAG, "doSearch about to start search");
            SearchEngine se = new SearchEngine(db);
            List<Hit> results = se.search(query);
            Log.d(TAG, "results num: " + results.size());
            return gson.toJson(new ApiResponse<List<Hit>>(results));
        } catch (Exception e) {
            return gson.toJson(new ApiResponse<>(
                    "Ошибка поиска",
                    e.getMessage() + "\n" + getStackTrace(e)
            ));
        }
    }

    @JavascriptInterface
    public String getFileContent(String path) {
        try {
            File file = new File(path);
            if (!file.exists() || !file.canRead()) {
                return gson.toJson(new ApiResponse<>(
                        "Файл не существует или не readable",
                        path
                ));
            }
            String content = new String(Files.readAllBytes((file).toPath()), StandardCharsets.UTF_8);
            return gson.toJson(new ApiResponse<String>(content));
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            return gson.toJson(new ApiResponse<>(
                    "Ошибка чтения файла",
                    path + ": " + e.getMessage() + "\n" + getStackTrace(e)
            ));
        }
    }

    @JavascriptInterface
    public void deleteIndexesForPaths (String jsonArrayString) {
        List<String> paths = gson.fromJson(jsonArrayString, new TypeToken<List<String>>(){}.getType());
        new Thread(() -> {
            Log.d(WebAppInterface.TAG, "deleting indexes for paths: " + jsonArrayString);
            DatabaseManager db = ((MainActivity) context).getDbManager();
            db.getConnection().beginTransaction();
            boolean success = false;
            try {
                for (String path : paths) {
                    db.deleteIndexForPath(path);
                }
                db.getConnection().setTransactionSuccessful();
                success = true;
                Log.d(WebAppInterface.TAG, "deleting indexes finished, returning folders...");
            } catch (Exception e) {
                sendResultToJS(webView, new ApiResponse<>("deleteIndexesForPaths",
                        "Ошибка при удалении индекса папкам",
                        e.getMessage() +  "\n" + getStackTrace(e)));
            } finally {
                db.getConnection().endTransaction();
            }

            if(success) {
                sendResultToJS(webView, new ApiResponse<>("deleteIndexesForPaths", db.getAllFolders()));
            }
        }).start();
    }

    @JavascriptInterface
    public void clearDB() {
        new Thread(() -> {
            LogTimer total = new LogTimer(WebAppInterface.TAG, false);
            total.log("SearchEngine: clearDB started");
            DatabaseManager db = ((MainActivity) context).getDbManager();
            boolean success = false;
            try {
                db.getConnection().beginTransaction();
                db.clearTables(false);
                db.getConnection().setTransactionSuccessful();
                total.log("SearchEngine: clearDB finished");
                success = true;
            } catch (Exception e) {
                sendResultToJS(webView, new ApiResponse<>("clearDB",
                        "ClearDB error",
                        e.getMessage()+ "\n" + getStackTrace(e)));
            } finally {
                db.getConnection().endTransaction();
            }

            if(success) {
                sendResultToJS(webView, new ApiResponse<>("clearDB", db.getAllFolders()));
            }
        }).start();
    }

    @JavascriptInterface
    public void updateIndex() {
        try {
            DatabaseManager db = ((MainActivity) context).getDbManager();
            FileIndexer fileIndexer = new FileIndexer(db, context);

            fileIndexer.setProgressCallback((type, payload) -> {
                sendResultToJS(webView, new ApiResponse<>(type, payload));
            });

            List<String> roots = db.getAllIndexedFolders();
            new Thread(() -> {
                File[] folders = roots.stream()
                    .map(File::new)
                    .toArray(File[]::new);
                boolean success = false;
                try {
                    db.getConnection().beginTransaction();
                    fileIndexer.updateIndex(folders);
                    db.getConnection().setTransactionSuccessful();
                    success = true;
                } catch (IOException | SQLException e) {
                    sendResultToJS(webView, new ApiResponse<>("updateIndex", "Ошибка индексации",
                            e.getMessage() + "\n" + getStackTrace(e)));
                } finally {
                    db.getConnection().endTransaction();
                }
                if (success) {
                    sendResultToJS(webView, new ApiResponse<>("updateIndex", db.getAllFolders()));
                }
            }).start();

        } catch (Exception e) {
            sendResultToJS(webView, new ApiResponse<>("updateIndex",
        "updateIndex error",
    e.getMessage()+ "\n" + getStackTrace(e)));
        }
    }

    @JavascriptInterface
    public void indexFolder(String path) {
        try {
            Log.d(TAG, "indexFolder called");
            File folder = new File(path);
            if (folder == null || !folder.exists() || !folder.isDirectory()) {
                Log.d(TAG, "Invalid folder");
                sendResultToJS(webView, new ApiResponse<>("indexFolder", "Папки нет или это не папка: ", path));
            }
            DatabaseManager db = ((MainActivity) context).getDbManager();
            FileIndexer fileIndexer = new FileIndexer(db, context);

            fileIndexer.setProgressCallback((type, payload) -> {
                sendResultToJS(webView, new ApiResponse<>(type, payload));
            });
            new Thread(() -> {
                File[] folders = new File[] { folder };
                boolean success = false;
                try {
                    db.getConnection().beginTransaction();
                    fileIndexer.updateIndex(folders);
                    db.getConnection().setTransactionSuccessful();
                    success = true;
                } catch (IOException | SQLException e) {
                    sendResultToJS(webView, new ApiResponse<>("indexFolder", "Ошибка индексации",
                e.getMessage() + "\n" + getStackTrace(e)));
                } finally {
                    db.getConnection().endTransaction();
                }
                if (success) {
                    sendResultToJS(webView, new ApiResponse<>("indexFolder", db.getAllFolders()));
                }
            }).start();
        } catch (Exception e) {
            sendResultToJS(webView, new ApiResponse<>("indexFolder", "indexFolder error", e.getMessage()+ "\n" + getStackTrace(e)));
        }
    }

    @JavascriptInterface
    public void rebuildIndex() {
        try {
            Log.d(WebAppInterface.TAG, "Deleting indexes before rebuilding index");
            DatabaseManager db = ((MainActivity) context).getDbManager();
            FileIndexer fileIndexer = new FileIndexer(db, context);

            fileIndexer.setProgressCallback((type, payload) -> {
                sendResultToJS(webView, new ApiResponse<>(type, payload));
            });

            List<String> roots = db.getAllIndexedFolders();
            new Thread(() -> {
                File[] folders = roots.stream()
                        .map(File::new)
                        .toArray(File[]::new);
                boolean success = false;
                try {
                    db.getConnection().beginTransaction();
                    db.clearTables(true);
                    fileIndexer.updateIndex(folders);
                    db.getConnection().setTransactionSuccessful();
                    success = true;
                } catch (IOException | SQLException e) {
                    sendResultToJS(webView, new ApiResponse<>("rebuildIndex",
                            "rebuildIndex error",
                            e.getMessage()+ "\n" + getStackTrace(e)));
                } finally {
                    db.getConnection().endTransaction();
                }
                if (success) {
                    sendResultToJS(webView, new ApiResponse<>("rebuildIndex", db.getAllFolders()));
                }
            }).start();
        } catch (Exception e) {
            sendResultToJS(webView, new ApiResponse<>("rebuildIndex",
                "rebuildIndex error",
                e.getMessage()+ "\n" + getStackTrace(e)));
        }
    }

    @JavascriptInterface
    public void selectFolder() {
        ((MainActivity) context).selectFolder();
    }

    @JavascriptInterface
    public String getIndexedFolders () {
        DatabaseManager db = ((MainActivity) context).getDbManager();
        List<Map<String, String>> res = db.getAllFolders();
        Log.d(TAG, "all folders: " + res.size());
        return gson.toJson(new ApiResponse<>("getIndexedFolders", db.getAllFolders()));
    }

    @JavascriptInterface
    public void exportDB() {
        //TODO: return something on front
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
}
