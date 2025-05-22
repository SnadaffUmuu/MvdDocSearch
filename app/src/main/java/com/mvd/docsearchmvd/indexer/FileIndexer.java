package com.mvd.docsearchmvd.indexer;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.util.Log;

import com.mvd.docsearchmvd.WebAppInterface;
import com.mvd.docsearchmvd.db.DatabaseManager;
import com.mvd.docsearchmvd.db.TokenDictionary;
import com.mvd.docsearchmvd.model.StatusUpdate;
import com.mvd.docsearchmvd.model.Token;
import com.mvd.docsearchmvd.model.ProgressUpdate;
import com.mvd.docsearchmvd.util.LogTimer;
import com.mvd.docsearchmvd.util.SettingsManager;

import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

public class FileIndexer {
    private final SQLiteDatabase conn;
    private final DatabaseManager db;
    private final Tokenizer tokenizer;
    private final TokenDictionary dict;
    private final String[] pathExceptions = {
            "."
    };

    private final SettingsManager settingsManager;
    private final Context context;
    private int totalFiles = 0;
    private int filesDone = 0;
    private long startTime;

    private BiConsumer<String, Object> progressCallback;

    public void setProgressCallback(BiConsumer<String, Object> callback) {
        this.progressCallback = callback;
    }

    public FileIndexer(DatabaseManager db, Context context, SettingsManager settingsManager) throws SQLException {
        this.context = context;
        this.conn = db.getConnection();
        this.db = db;
        this.tokenizer = new Tokenizer();
        this.dict = new TokenDictionary(conn);
        this.settingsManager = settingsManager;
    }

    private boolean isAllowedExtension(File file) {
        String name = file.getName().toLowerCase();
        return settingsManager.getAllowedExtensions()
                .stream()
                .anyMatch(ext -> name.endsWith(ext.toLowerCase(Locale.ROOT)));
    }

    private boolean toExclude(File res) {
        String name = res.getName().toLowerCase();
        return Arrays.stream(pathExceptions).anyMatch(name::startsWith);
    }

    private void indexFile(File file, String content) {
        Log.d(WebAppInterface.TAG, "indexFile " + file.getName());

        int fileId;
        fileId = db.getFileId(file.getAbsolutePath());
        Log.d(WebAppInterface.TAG, "tokenize and insert: " + file.getName());
        LogTimer getTokens = new LogTimer(true);
        if (progressCallback != null) {
            progressCallback.accept("statusUpdate", new StatusUpdate("tokenizing file [" + file.getName() + "] started..."));
        }

        List<Token> tokens = tokenizer.tokenize(content);

        if (progressCallback != null) {
            progressCallback.accept("statusUpdate",
                new StatusUpdate(
            "tokenizing [" + file.getName() + "] finished (" + tokens.size() + " tokens)",
                  getTokens.getElapsed()));
        }

        LogTimer tokensInsertTT = new LogTimer(true);
        if (progressCallback != null) {
            progressCallback.accept("statusUpdate", new StatusUpdate("inserting tokens [" + file.getName() + "] started..."));
        }

        String sql = "INSERT INTO tokens (token_id, file_id, positions) VALUES (?, ?, ?)";
        SQLiteStatement stmt = conn.compileStatement(sql);

        for (Token token : tokens) {
            try {
                int tokenId = dict.getTokenId(token.text);
                stmt.clearBindings();
                stmt.bindLong(1, tokenId);
                stmt.bindLong(2, fileId);
                stmt.bindString(3, token.positions);
                stmt.executeInsert();
            } catch (Exception e) {
                Log.e(WebAppInterface.TAG, "Ошибка вставки токена: " + token.text, e);
            }
        }

        stmt.close();

        if (progressCallback != null) {
            progressCallback.accept("statusUpdate", new StatusUpdate("inserting tokens [" + file.getName() + "] finished", tokensInsertTT.getElapsed()));
        }
        Log.d(WebAppInterface.TAG, "[" + file.getName() + "] tokens inserted");
    }

    public void indexFileIfNeeded(File file) throws SQLException, IOException {
        Log.d(WebAppInterface.TAG, "indexFileIfNeeded");

        String content = db.updateFileMetadata(file);

        Log.d(WebAppInterface.TAG, "file content received");
        if (content != null) {
            indexFile(file, content);
        }
    }

    /*
    //Использовался до того, как реализовали прогресс-бар и предварительный сбор всех файлов
    public void indexFilesInDirectory(File resource) throws SQLException, IOException {
        Log.d(WebAppInterface.TAG, "indexFilesInDirectory");
        Log.d(WebAppInterface.TAG, resource.getName());
        if (resource.isDirectory()) {
            File[] files = resource.listFiles();
            if (files == null) return;
            for (File file : files) {
                indexFilesInDirectory(file);
            }
        } else if (resource.isFile() && resource.canRead()) {
            Log.d(WebAppInterface.TAG, "resource is file");
            if (isAllowedExtension(resource)) {
                Log.d(WebAppInterface.TAG, "allowed extension");
                indexFileIfNeeded(resource);
            } else {
                Log.d(WebAppInterface.TAG, "Incorrect extension: " + resource.getName());
            }
        }
    }
     */

    public void updateIndex(File[] folders) throws IOException, SQLException {
        Log.d(WebAppInterface.TAG, "updateIndex");
        LogTimer updateIndexInnerTotal = new LogTimer(true);
        if (progressCallback != null) {
            progressCallback.accept("statusUpdate", new StatusUpdate("updateIndexInner started..."));
        }

        LogTimer collectFilesTimer = new LogTimer(false);
        if (progressCallback != null) {
            progressCallback.accept("statusUpdate", new StatusUpdate("collecting files started..."));
        }

        List<File> allResources = collectAllResources(folders);

        Set<String> actualPaths = allResources.stream()
                .map(File::getAbsolutePath)
                .collect(Collectors.toSet());

        if (progressCallback != null) {
            progressCallback.accept("statusUpdate", new StatusUpdate("collecting files finished", collectFilesTimer.getElapsed()));
        }

        LogTimer deleteFilesTimer = new LogTimer(false);
        if (progressCallback != null) {
            progressCallback.accept("statusUpdate", new StatusUpdate("deleting missing files from db..."));
        }

        db.deleteMissingFilesFromDb(actualPaths, folders);

        if (progressCallback != null) {
            progressCallback.accept("statusUpdate", new StatusUpdate("deleting missing files finished", deleteFilesTimer.getElapsed()));
        }

        filesDone = 0;
        startTime = System.currentTimeMillis();
        totalFiles = allResources.size();
        Log.d(WebAppInterface.TAG, "allResources size: " + totalFiles);
        for (File resource : allResources) {
            Log.d(WebAppInterface.TAG, resource.getAbsolutePath());

            indexFileIfNeeded(resource);

            filesDone++;
            long elapsed = (System.currentTimeMillis() - startTime) / 1000;
            if (progressCallback != null) {
                progressCallback.accept("indexProgress", new ProgressUpdate(resource.getName(), filesDone, totalFiles, elapsed));
            }
        }
        if (progressCallback != null) {
            progressCallback.accept("statusUpdate", new StatusUpdate("updateIndexInner finished", updateIndexInnerTotal.getElapsed()));
        }
    }

    private List<File> collectAllResources(File[] roots) {
        List<File> result = new ArrayList<>();
        Log.d(WebAppInterface.TAG, "collectAllResources called");

        for (File root : roots) {
            Log.d(WebAppInterface.TAG, "root folder:" + root.getAbsolutePath());

            if (db.rootExists(root) && (root == null || !root.exists())) {
                Log.d(WebAppInterface.TAG, "root folder:" + root.getAbsolutePath() + " есть в базе, но нет на диске, удаляем из базы");

                db.deleteIndexForPath(root.getAbsolutePath());

            } else if (!db.rootExists(root)) {
                Log.d(WebAppInterface.TAG, "root folder:" + root.getAbsolutePath() + " нет в базе, но есть на диске, добавляет в бд");
                Log.d(WebAppInterface.TAG, "folder doesn't exists in db yet, inserting");

                db.insertIndexedFolder(root);

                walk(root, result);

            } else {
                Log.d(WebAppInterface.TAG, "folder already exists in indexed_folders");

                walk(root, result);
            }
        }
        return result;
    }

    private void walk(File file, List<File> result) {
        if (file.isDirectory() && !toExclude(file)) {
            Log.d(WebAppInterface.TAG, "file is folder:" + file.getAbsolutePath());
            File[] files = file.listFiles();
            Log.d(WebAppInterface.TAG, "the folder contains files: " + files.length);
            if (files != null) {
                for (File f : files) walk(f, result);
            }
        } else if (file.isFile() && file.canRead() && !toExclude(file) && isAllowedExtension(file)) {
            Log.d(WebAppInterface.TAG, "adding file: " + file.getAbsolutePath());
            result.add(file);
        }
    }
}
