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
import com.mvd.docsearchmvd.model.StatusUpdate;
import com.mvd.docsearchmvd.search.SearchEngine;

import com.google.gson.Gson;

import com.mvd.docsearchmvd.model.ApiResponse;
import com.mvd.docsearchmvd.util.LogTimer;
import com.mvd.docsearchmvd.util.NativeLogger;
import com.mvd.docsearchmvd.util.SettingsManager;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WebAppInterface {
    public static final String TAG = "DocSearchMvdLog";
    private Context context;
    private WebView webView;
    private final Gson gson = new Gson();
    private SettingsManager settingsManager;

    WebAppInterface(Context ctx, WebView webView) {
        this.context = ctx;
        this.webView = webView;
        this.settingsManager = new SettingsManager(context);
    }
    @JavascriptInterface
    public String doSearch(String query) {
        try {
            NativeLogger.resetLog();
            LogTimer total = new LogTimer(true);

            sendResultToJS(webView, new ApiResponse<>("search",
                    new StatusUpdate("search started " + query)));
//            NativeLogger.writeResultToFile(new ApiResponse<>("search",
//                  new StatusUpdate("search started " + query)));

            Log.d(TAG, "back: doSearch called, query: " + query);

            DatabaseManager db = ((MainActivity) context).getDbManager();

            db.setProgressCallback((type, payload) -> {
//                sendResultToJS(webView, new ApiResponse<>(type, payload));
//                NativeLogger.writeResultToFile(new ApiResponse<>(type, payload));
            });

            SearchEngine se = new SearchEngine(db);

            se.setProgressCallback((type, payload) -> {
//                sendResultToJS(webView, new ApiResponse<>(type, payload));
//                NativeLogger.writeResultToFile(new ApiResponse<>(type, payload));
            });

            List<Hit> results = se.search(query);

            Log.d(TAG, "results num: " + results.size());

            sendResultToJS(webView, new ApiResponse<>("search",
                    new StatusUpdate("search finished", total.getElapsed())));
//            NativeLogger.writeResultToFile(new ApiResponse<>("search",
//                    new StatusUpdate("search finished", total.getElapsed())));

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
            sendResultToJS(webView, new ApiResponse<>("statusUpdate",
                    new StatusUpdate("deleteIndexesForPaths started (" + paths.size() + " папок)")));
            LogTimer timer = new LogTimer(false);
            DatabaseManager db = ((MainActivity) context).getDbManager();

            db.setProgressCallback((type, payload) -> {
                sendResultToJS(webView, new ApiResponse<>(type, payload));
            });

            Map<String, Integer> stats = new HashMap<>();
            db.setStatCallback((type, num) -> {
                stats.merge(type, (Integer) num, Integer::sum);
            });

            db.getConnection().beginTransaction();
            boolean success = false;
            try {
                for (String path : paths) {
                    LogTimer fileIndexDeleteTimer = new LogTimer(false);
                    db.deleteIndexForPath(path);
                    db.deleteOrphanTerms();
                    sendResultToJS(webView, new ApiResponse<>("statusUpdate", new StatusUpdate("Deleted for path " + path, fileIndexDeleteTimer.getElapsed())));
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
                sendResultToJS(webView, new ApiResponse<>("statusFinish",
                        new StatusUpdate("deleteIndexesForPaths",
                            timer.getElapsed()))
                );
                sendResultToJS(webView, new ApiResponse<>("deleteIndexesForPaths", db.getAllFolders()));
            }
        }).start();
    }

    @JavascriptInterface
    public void clearDB() {
        new Thread(() -> {
            sendResultToJS(webView, new ApiResponse<>("statusUpdate",
                            new StatusUpdate("clearDB started")));
            LogTimer total = new LogTimer(WebAppInterface.TAG, false);
            DatabaseManager db = ((MainActivity) context).getDbManager();
            db.setProgressCallback((type, payload) -> {
                sendResultToJS(webView, new ApiResponse<>(type, payload));
            });
            boolean success = false;
            try {
                db.getConnection().beginTransaction();
                db.clearTables(false);
                db.getConnection().setTransactionSuccessful();
                success = true;
            } catch (Exception e) {
                sendResultToJS(webView, new ApiResponse<>("clearDB",
                        "ClearDB error",
                        e.getMessage()+ "\n" + getStackTrace(e)));
            } finally {
                db.getConnection().endTransaction();
            }

            if(success) {
                sendResultToJS(webView, new ApiResponse<>("statusFinish",
                        new StatusUpdate("clearDB",
                                total.getElapsed()))
                );
                sendResultToJS(webView, new ApiResponse<>("clearDB", db.getAllFolders()));
            }
        }).start();
    }

    @JavascriptInterface
    public void updateIndex(String jsonArrayString) {
        try {

            DatabaseManager db = ((MainActivity) context).getDbManager();
            FileIndexer fileIndexer = new FileIndexer(db, context, settingsManager);

            fileIndexer.setProgressCallback((type, payload) -> {
                sendResultToJS(webView, new ApiResponse<>(type, payload));
            });
            db.setProgressCallback((type, payload) -> {
                sendResultToJS(webView, new ApiResponse<>(type, payload));
            });

            List<String> rawRoots = gson.fromJson(jsonArrayString, new TypeToken<List<String>>(){}.getType());
            List<String> roots = rawRoots.isEmpty() ? db.getAllIndexedFolders() : rawRoots;

            new Thread(() -> {
                File[] rootPathsFromClient = roots.stream()
                    .map(File::new)
                    .toArray(File[]::new);
                boolean success = false;
                LogTimer updateIndexTotal = new LogTimer(true);
                try {
                    sendResultToJS(webView, new ApiResponse<>("statusUpdate",
                            new StatusUpdate("\uD83D\uDD01 Reindex started (" + roots.size() + " root folders)")));
                    db.getConnection().beginTransaction();

                    fileIndexer.updateIndex(rootPathsFromClient);

                    db.getConnection().setTransactionSuccessful();
                    success = true;
                } catch (IOException | SQLException e) {
                    sendResultToJS(webView, new ApiResponse<>("updateIndex", "Ошибка индексации",
                            e.getMessage() + "\n" + getStackTrace(e)));
                } finally {
                    db.getConnection().endTransaction();
                }
                if (success) {
                    sendResultToJS(webView, new ApiResponse<>("statusFinish",
                            new StatusUpdate("✅ Reindex completed", updateIndexTotal.getElapsed()))
                    );
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
            LogTimer indexFolderTotal = new LogTimer(true);
            sendResultToJS(webView, new ApiResponse<>("statusUpdate",
                    new StatusUpdate("indexFolder started")));

            File folder = new File(path);
            if (folder == null || !folder.exists() || !folder.isDirectory()) {
                Log.d(TAG, "Invalid folder");
                sendResultToJS(webView, new ApiResponse<>("indexFolder", "Папки нет или это не папка: ", path));
                return;
            }
            DatabaseManager db = ((MainActivity) context).getDbManager();
            FileIndexer fileIndexer = new FileIndexer(db, context, settingsManager);

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
                    sendResultToJS(webView, new ApiResponse<>("statusFinish",
                            new StatusUpdate("indexFolder finished", indexFolderTotal.getElapsed()))
                    );
                    sendResultToJS(webView, new ApiResponse<>("indexFolder", db.getAllFolders()));
                }
            }).start();
        } catch (Exception e) {
            sendResultToJS(webView, new ApiResponse<>("indexFolder", "indexFolder error", e.getMessage()+ "\n" + getStackTrace(e)));
        }
    }

    @JavascriptInterface
    public void rebuildIndex(String jsonArrayString) {
        try {
            Log.d(WebAppInterface.TAG, "Deleting indexes before rebuilding index");
            sendResultToJS(webView, new ApiResponse<>("statusUpdate",
                    new StatusUpdate("rebuildIndex started")));
            DatabaseManager db = ((MainActivity) context).getDbManager();
            FileIndexer fileIndexer = new FileIndexer(db, context, settingsManager);

            fileIndexer.setProgressCallback((type, payload) -> {
                sendResultToJS(webView, new ApiResponse<>(type, payload));
            });

            List<String> rawRoots = gson.fromJson(jsonArrayString, new TypeToken<List<String>>(){}.getType());
            List<String> roots = rawRoots.isEmpty() ? db.getAllIndexedFolders() : rawRoots;

            new Thread(() -> {
                LogTimer totalTimer = new LogTimer(true);
                sendResultToJS(webView, new ApiResponse<>("statusUpdate",
                        new StatusUpdate("Rebuilding index...")));

                File[] folders = roots.stream()
                        .map(File::new)
                        .toArray(File[]::new);
                boolean success = false;
                try {
                    db.getConnection().beginTransaction();

                    LogTimer clearTablesTimer = new LogTimer(true);

                    sendResultToJS(webView, new ApiResponse<>("statusUpdate",
                            new StatusUpdate("Clearing tables...")));

                    db.clearTables(true);

                    sendResultToJS(webView, new ApiResponse<>("statusUpdate",
                            new StatusUpdate("Tables cleared", clearTablesTimer.getElapsed())));

                    LogTimer indexingTotal = new LogTimer(true);

                    sendResultToJS(webView, new ApiResponse<>("statusUpdate",
                            new StatusUpdate("Updating index...")));

                    fileIndexer.updateIndex(folders);

                    db.getConnection().setTransactionSuccessful();
                    success = true;

                    sendResultToJS(webView, new ApiResponse<>("statusUpdate",
                            new StatusUpdate("Updating index finished", indexingTotal.getElapsed())));
                } catch (IOException | SQLException e) {
                    sendResultToJS(webView, new ApiResponse<>("rebuildIndex",
                            "rebuildIndex error",
                            e.getMessage()+ "\n" + getStackTrace(e)));
                } finally {
                    db.getConnection().endTransaction();
                }
                if (success) {
                    sendResultToJS(webView, new ApiResponse<>("statusFinish",
                            new StatusUpdate("Rebuilding index finished", totalTimer.getElapsed()))
                    );
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

    @JavascriptInterface
    public void requestCurrentExtensions() {
        String current = settingsManager.getAllowedExtensionsRaw();
        final String js = "showPromptWithDefaults(" + JSONObject.quote(current) + ")";
        webView.post(() -> webView.evaluateJavascript(js, null));
    }

    @JavascriptInterface
    public void saveExtensions(String csv) {
        settingsManager.saveAllowedExtensions(csv);
    }
}
