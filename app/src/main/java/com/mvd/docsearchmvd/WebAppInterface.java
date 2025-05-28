package com.mvd.docsearchmvd;

import static com.mvd.docsearchmvd.util.Util.getStackTrace;
import static com.mvd.docsearchmvd.util.Util.sendResultToJS;

import android.app.NotificationManager;
import android.content.*;
import android.webkit.JavascriptInterface;
import android.util.Log;
import android.webkit.WebView;

import androidx.core.app.NotificationCompat;

import com.google.gson.reflect.TypeToken;
import com.mvd.docsearchmvd.db.DatabaseManager;
import com.mvd.docsearchmvd.indexer.FileIndexer;
import com.mvd.docsearchmvd.model.Hit;
import com.mvd.docsearchmvd.model.StatusUpdate;
import com.mvd.docsearchmvd.search.SearchEngine;
import com.mvd.docsearchmvd.model.ProgressUpdate;

import com.google.gson.Gson;

import com.mvd.docsearchmvd.model.ApiResponse;
import com.mvd.docsearchmvd.util.LogTimer;
import com.mvd.docsearchmvd.util.Profiler;
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
    private NotificationManager notificationManager;
    private NotificationCompat.Builder notificationBuilder;
    private static final int NOTIFICATION_ID = 1001;

    WebAppInterface(Context ctx, WebView webView) {
        Log.d(WebAppInterface.TAG, "WebAppInterface constructor called, context: " + ctx);
        this.context = ctx;
        this.webView = webView;
        this.settingsManager = new SettingsManager(context);
    }
    @JavascriptInterface
    public String doSearch(String query) {
        try {
            LogTimer total = new LogTimer(true);

            sendResultToJS(webView, new ApiResponse<>("search",
                    new StatusUpdate("Search started " + query)));

            Log.d(TAG, "back: doSearch called, query: " + query);

            DatabaseManager db = ((MainActivity) context).getDbManager();

            SearchEngine se = new SearchEngine(db);

            se.setProgressCallback((type, payload) -> {
                sendResultToJS(webView, new ApiResponse<>(type, payload));
            });

            List<Hit> results = se.search(query);

            Log.d(TAG, "results num: " + results.size());

            sendResultToJS(webView, new ApiResponse<>("search",
                    new StatusUpdate("search finished", total.getElapsed())));

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
                    sendResultToJS(webView, new ApiResponse<>("statusUpdate", new StatusUpdate("Deleted for path "
                            + path, fileIndexDeleteTimer.getElapsed())));
                }
                LogTimer orphans = new LogTimer(true);

                db.deleteOrphanTerms();

                String orphansS = "<br>- deleting orphan terms: " + orphans.getElapsed();
                db.getConnection().setTransactionSuccessful();
                success = true;

                String message = "Deleting summary:";
                for(String metric : stats.keySet()) {
                    message += "<br>- " + metric + ": " + stats.get(metric);
                }
                message += "<br>- avg TOTAL root delete time: " + Profiler.get("deleting").getAverage()
                        + "<br>- avg FILES del time: " + Profiler.get("files").getAverage()
                        + "<br>- avg FOLDERS del time: " + Profiler.get("folders").getAverage()
                        + orphansS;
                Profiler.clear();
                sendResultToJS(webView, new ApiResponse<>("statusUpdate", new StatusUpdate(message)));

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
                if ("indexProgress".equals(type) && payload instanceof ProgressUpdate) {
                    ProgressUpdate progress = (ProgressUpdate) payload;
                    updateNotificationProgress(progress.filesDone, progress.totalFiles, progress.fileName);
                }
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
                    startNotification("Updating");
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
                    finishNotification("Updating finished");
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
            db.setProgressCallback((type, payload) -> {
                sendResultToJS(webView, new ApiResponse<>(type, payload));
            });
            FileIndexer fileIndexer = new FileIndexer(db, context, settingsManager);
            fileIndexer.setProgressCallback((type, payload) -> {
                sendResultToJS(webView, new ApiResponse<>(type, payload));
                if ("indexProgress".equals(type) && payload instanceof ProgressUpdate) {
                    ProgressUpdate progress = (ProgressUpdate) payload;
                    updateNotificationProgress(progress.filesDone, progress.totalFiles, progress.fileName);
                }
            });

            new Thread(() -> {
                File[] folders = new File[] { folder };
                boolean success = false;
                try {
                    db.getConnection().beginTransaction();
                    startNotification("Indexing");
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
                    finishNotification("Indexing finished");
                }
            }).start();
        } catch (Exception e) {
            sendResultToJS(webView, new ApiResponse<>("indexFolder", "indexFolder error", e.getMessage()+ "\n" + getStackTrace(e)));
        }
    }

    @JavascriptInterface
    public void rebuildIndex(String jsonArrayString) {
        try {
            sendResultToJS(webView, new ApiResponse<>("statusUpdate",
                    new StatusUpdate("rebuildIndex started")));
            DatabaseManager db = ((MainActivity) context).getDbManager();
            FileIndexer fileIndexer = new FileIndexer(db, context, settingsManager);

            fileIndexer.setProgressCallback((type, payload) -> {
                sendResultToJS(webView, new ApiResponse<>(type, payload));
                if ("indexProgress".equals(type) && payload instanceof ProgressUpdate) {
                    ProgressUpdate progress = (ProgressUpdate) payload;
                    updateNotificationProgress(progress.filesDone, progress.totalFiles, progress.fileName);
                }
            });
            db.setProgressCallback((type, payload) -> {
                sendResultToJS(webView, new ApiResponse<>(type, payload));
            });

            List<String> rawRoots = gson.fromJson(jsonArrayString, new TypeToken<List<String>>(){}.getType());
            List<String> roots = rawRoots.isEmpty() ? db.getAllIndexedFolders() : rawRoots;

            new Thread(() -> {
                LogTimer totalTimer = new LogTimer(true);

                File[] folders = roots.stream()
                        .map(File::new)
                        .toArray(File[]::new);
                boolean success = false;
                try {
                    sendResultToJS(webView, new ApiResponse<>("statusUpdate",
                            new StatusUpdate("\uD83D\uDD01 Rebuilding started (" + roots.size() + " root folders)")));
                    db.getConnection().beginTransaction();
                    startNotification("Rebuilding");
                    LogTimer clearTablesTimer = new LogTimer(true);

                    sendResultToJS(webView, new ApiResponse<>("statusUpdate",
                            new StatusUpdate("Clearing tables...")));
                    Log.d(WebAppInterface.TAG, "Deleting indexes before rebuilding index");

                    db.clearTables(true);

                    sendResultToJS(webView, new ApiResponse<>("statusUpdate",
                            new StatusUpdate("Tables cleared", clearTablesTimer.getElapsed())));

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
                    sendResultToJS(webView, new ApiResponse<>("statusFinish",
                            new StatusUpdate("Rebuilding index finished", totalTimer.getElapsed()))
                    );
                    sendResultToJS(webView, new ApiResponse<>("rebuildIndex", db.getAllFolders()));
                    finishNotification("Rebuilding finished");
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
        Log.d(WebAppInterface.TAG, "getIndexedFolders called, dbManager: " + ((MainActivity) context).getDbManager());
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
        final String js = "showExceptionsPrompt(" + JSONObject.quote(current) + ")";
        webView.post(() -> webView.evaluateJavascript(js, null));
    }

    @JavascriptInterface
    public void requestCurrentExcludedPaths() {
        String current = settingsManager.getExcludedPathsRaw();
        final String js = "showPathsPrompt(" + JSONObject.quote(current) + ")";
        webView.post(() -> webView.evaluateJavascript(js, null));
    }

    @JavascriptInterface
    public void saveExtensions(String csv) {
        settingsManager.saveAllowedExtensions(csv);
    }

    @JavascriptInterface
    public void saveExcludedPaths(String csv) {
        settingsManager.saveExcludedPaths(csv);
    }

    private void startNotification(String title) {
        notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationBuilder = new NotificationCompat.Builder(context, "index_channel_id")
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle(title)
                .setContentText("Индексация началась…")
                .setProgress(100, 0, true)
                .setOngoing(true)
                .setOnlyAlertOnce(true);
        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
    }

    private void updateNotificationProgress(int done, int total, String filename) {
        if (notificationBuilder == null || notificationManager == null) return;
        int progress = total > 0 ? (100 * done / total) : 0;
        notificationBuilder
                .setContentText("Файл: " + filename)
                .setProgress(100, progress, false);
        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
    }

    private void finishNotification(String message) {
        if (notificationBuilder == null || notificationManager == null) return;
        notificationBuilder
                .setContentText(message)
                .setProgress(0, 0, false)
                .setOngoing(false);
        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
    }
}
