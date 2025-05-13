package com.mvd.docsearchmvd.indexer;

import androidx.documentfile.provider.DocumentFile;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.util.Log;

import com.mvd.docsearchmvd.WebAppInterface;
import com.mvd.docsearchmvd.db.DatabaseManager;
import com.mvd.docsearchmvd.db.TokenDictionary;

import java.io.*;
import java.sql.*;
import java.util.*;

public class FileIndexer {
    private final SQLiteDatabase conn;
    private final DatabaseManager db;
    private final Tokenizer tokenizer;
    private final TokenDictionary dict;
    private final String[] extensions = {
            "txt",
            "json",
            "srt",
            "ass",
            "js",
            "fb2"
    };
    private final Context context;
    private IndexProgressListener progressListener;
    private int totalFiles = 0;
    private int filesDone = 0;

    public FileIndexer(DatabaseManager db, Context context) throws SQLException {
        this.context = context;
        this.conn = db.getConnection();
        this.db = db;
        this.tokenizer = new Tokenizer();
        this.dict = new TokenDictionary(conn);
    }

    public interface IndexProgressListener {
        void onFileIndexed(String fileName, int filesDone, int totalFiles);
    }

    public void setProgressListener(IndexProgressListener listener) {
        this.progressListener = listener;
    }

    private boolean isAllowedExtension(File file) {
        String name = file.getName().toLowerCase();
        return Arrays.stream(extensions).anyMatch(name::endsWith);
    }

    private void indexFile(File file, String content) {
        Log.d(WebAppInterface.TAG, "indexFile");

        int fileId;
        fileId = db.getFileId(file.getAbsolutePath());
        Log.d(WebAppInterface.TAG, "tokenize");
        List<Token> tokens = tokenizer.tokenize(content);
        Log.d(WebAppInterface.TAG, "Tokens ready: " + tokens.size() + "; inserting into db");
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
        Log.d(WebAppInterface.TAG, "tokens inserted");
        stmt.close();
    }

    public void indexFileIfNeeded(File file) throws SQLException, IOException {
        Log.d(WebAppInterface.TAG, "indexFileIfNeeded");
        String content = db.updateFileMetadata(file);
        if (content != null) {
            indexFile(file, content);
        }
    }

    public void indexFilesInDirectory(File folder) throws SQLException, IOException {
        Log.d(WebAppInterface.TAG, "indexFilesInDirectory");
        Log.d(WebAppInterface.TAG, folder.getName());
        if (folder.isDirectory()) {
            File[] files = folder.listFiles();
            if (files == null) return;
            for (File file : files) {
                indexFilesInDirectory(file);
            }
        } else if (folder.isFile() && folder.canRead()) {
            Log.d(WebAppInterface.TAG, "folder is file");
            if (isAllowedExtension(folder)) {
                Log.d(WebAppInterface.TAG, "allowed extension");
                indexFileIfNeeded(folder);
            } else {
                Log.d(WebAppInterface.TAG, "Incorrect extension: " + folder.getName());
            }
        }
    }

    public void updateIndex(File[] folders) throws IOException, SQLException {
        Log.d(WebAppInterface.TAG, "updateIndex");
        List<File> allFiles = collectAllFiles(folders);
        totalFiles = allFiles.size();
        Log.d(WebAppInterface.TAG, "allFiles size: " + totalFiles);
        filesDone = 0;
        conn.beginTransaction();
        try {
            for (File file : allFiles) {
                Log.d(WebAppInterface.TAG, file.getAbsolutePath());
                //indexFilesInDirectory(dir);
                indexFileIfNeeded(file);
                filesDone++;
                Log.d(WebAppInterface.TAG, "Progress, files Done: " + filesDone);
                if (progressListener != null) {
                    Log.d(WebAppInterface.TAG, "calling onFileIndexed");
                    progressListener.onFileIndexed(file.getName(), filesDone, totalFiles);
                }
            }
            conn.setTransactionSuccessful();
        } catch (Exception e) {
            Log.e(WebAppInterface.TAG, "Ошибка при обновлении индекса", e);
            throw e;
        } finally {
            conn.endTransaction();
        }
    }

    private List<File> collectAllFiles(File[] roots) {
        List<File> result = new ArrayList<>();
        Log.d(WebAppInterface.TAG, "collectAllFiles called");
        for (File root : roots) {
            Log.d(WebAppInterface.TAG, "root folder:" + root.getAbsolutePath());
            if (!db.rootExists(root)) {
                Log.d(WebAppInterface.TAG, "folder doesn't exists in db yet, inserting");
                db.insertIndexedFolder(root);
                walk(root, result);
                //TODO: make feedback if exists
            } else {
                Log.d(WebAppInterface.TAG, "folder already exists in indexed_folders");
            }
        }
        return result;
    }

    private void walk(File file, List<File> result) {
        if (file.isDirectory()) {
            Log.d(WebAppInterface.TAG, "file is folder:" + file.getAbsolutePath());
            File[] files = file.listFiles();
            Log.d(WebAppInterface.TAG, "the folder contains files: " + files.length);
            if (files != null) {
                for (File f : files) walk(f, result);
            }
        } else if (file.isFile() && file.canRead() && isAllowedExtension(file)) {
            Log.d(WebAppInterface.TAG, "adding file: " + file.getAbsolutePath());
            result.add(file);
        }
    }
}
