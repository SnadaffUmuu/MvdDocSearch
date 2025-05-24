package com.mvd.docsearchmvd.db;

import static com.mvd.docsearchmvd.util.Util.formatElapsed;
import static com.mvd.docsearchmvd.util.Util.formatUnitTime;
import static com.mvd.docsearchmvd.util.Util.mergeSortedLists;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.util.Log;

import com.mvd.docsearchmvd.WebAppInterface;
import com.mvd.docsearchmvd.model.FileEntry;
import com.mvd.docsearchmvd.model.StatusUpdate;
import com.mvd.docsearchmvd.util.LogTimer;
import com.mvd.docsearchmvd.util.Profiler;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.function.BiConsumer;

public class DatabaseManager {
    private final SQLiteDatabase db;
    private final Context context;

    private BiConsumer<String, Object> progressCallback;

    public void setProgressCallback(BiConsumer<String, Object> callback) {
        this.progressCallback = callback;
    }

    private BiConsumer<String, Object> statCallback;

    public void setStatCallback(BiConsumer<String, Object> callback) {
        this.statCallback = callback;
    }

    public DatabaseManager(Context context) {
        this.context = context;
        String dbPath = context.getFilesDir().getAbsolutePath() + "/index.db";
        this.db = SQLiteDatabase.openOrCreateDatabase(dbPath, null);
        db.execSQL("PRAGMA foreign_keys = ON;");
    }
    public SQLiteDatabase getConnection() {
        return db;
    }
    public void init(boolean clear) {
        Log.d(WebAppInterface.TAG, "Init db");
        try {
//            Log.d(WebAppInterface.TAG, "create dict if not exists");
            db.execSQL("CREATE TABLE IF NOT EXISTS dict (id INTEGER PRIMARY KEY, token TEXT UNIQUE);");

//            Log.d(WebAppInterface.TAG, "create files if not exists");
            db.execSQL("CREATE TABLE IF NOT EXISTS files(" +
                    "id INTEGER PRIMARY KEY, " +
                    "path TEXT UNIQUE NOT NULL, " +
                    "size INTEGER NOT NULL, " +
                    "last_modified INTEGER NOT NULL);");

//            Log.d(WebAppInterface.TAG, "create tokens if not exists");
            db.execSQL("CREATE TABLE IF NOT EXISTS tokens(" +
                    "token_id INTEGER, " +
                    "file_id INTEGER NOT NULL, " +
                    "positions_blob BLOB NOT NULL, " +
                    "FOREIGN KEY(file_id) REFERENCES files(id) ON DELETE CASCADE, " +
                    "FOREIGN KEY(token_id) REFERENCES dict(id));");

//            Log.d(WebAppInterface.TAG, "create indexed_folders if not exists");
            db.execSQL("CREATE TABLE  IF NOT EXISTS indexed_folders(" +
                    "id INTEGER PRIMARY KEY, " +
                    "path TEXT NOT NULL UNIQUE);");

            db.execSQL("CREATE INDEX IF NOT EXISTS idx_files_path " +
                            "ON files(path)"
            );

            db.execSQL("CREATE INDEX IF NOT EXISTS idx_tokens_token_id " +
                    "ON tokens(token_id)");

            db.execSQL("CREATE INDEX IF NOT EXISTS idx_tokens_token_id_file_id " +
                    "ON tokens(" +
                    "token_id, " +
                    "file_id " +
                    ");");

            db.execSQL("CREATE INDEX IF NOT EXISTS idx_tokens_file_id " +
                    "ON tokens(file_id);");


            db.execSQL("CREATE INDEX IF NOT EXISTS idx_dict_token ON dict(token);");

            if (clear) {
                db.execSQL("DELETE FROM tokens;");
                db.execSQL("DELETE FROM files;");
                db.execSQL("DELETE FROM dict;");
                db.execSQL("DELETE FROM indexed_folders;");
//                Log.d(WebAppInterface.TAG, "[INFO] База очищена");
            }
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            Log.e(WebAppInterface.TAG, sw.toString());
        }
    }


    public void clearTables(boolean keepRoots) {

        LogTimer delTokens = new LogTimer(WebAppInterface.TAG, true);

        if (progressCallback != null) {
            progressCallback.accept("statusUpdate", new StatusUpdate("clearing tokens..."));
        }

        db.execSQL("DELETE FROM tokens;");

        if (progressCallback != null) {
            progressCallback.accept("statusUpdate", new StatusUpdate("clearing tokens finished", delTokens.getElapsed()));
        }

        LogTimer delFiles = new LogTimer(WebAppInterface.TAG, true);

        if (progressCallback != null) {
            progressCallback.accept("statusUpdate", new StatusUpdate("clearing files..."));
        }

        db.execSQL("DELETE FROM files;");

        if (progressCallback != null) {
            progressCallback.accept("statusUpdate", new StatusUpdate("clearing files finished", delFiles.getElapsed()));
        }

        LogTimer delDict = new LogTimer(WebAppInterface.TAG, true);

        if (progressCallback != null) {
            progressCallback.accept("statusUpdate", new StatusUpdate("clearing dict..."));
        }

        db.execSQL("DELETE FROM dict;");

        if (progressCallback != null) {
            progressCallback.accept("statusUpdate", new StatusUpdate("clearing dict finished", delDict.getElapsed()));
        }

        LogTimer delRoots = new LogTimer(WebAppInterface.TAG, true);

        if (progressCallback != null) {
            progressCallback.accept("statusUpdate", new StatusUpdate("clearing indexed_folders..."));
        }

        if (!keepRoots) {
            db.execSQL("DELETE FROM indexed_folders;");
        }

        if (progressCallback != null) {
            progressCallback.accept("statusUpdate", new StatusUpdate("clearing indexed_folders finished", delRoots.getElapsed()));
        }
    }

    public String updateFileMetadata(FileEntry entry) throws IOException {
        long start = System.currentTimeMillis();
        Log.d(WebAppInterface.TAG, "updateFileMetadata");

        if (progressCallback != null) {
            progressCallback.accept("statusUpdate", new StatusUpdate("Updating metadata (" + entry.file.getName() + ")..."));
        }

        String filePath = entry.getPath();
        long fileSize = entry.size;
        long lastModified = entry.lastModified;
        Cursor cursor = db.rawQuery("SELECT size, last_modified FROM files WHERE path = ?", new String[]{filePath});

        try {
            if (cursor.moveToFirst()) {
                // Если файл уже есть в базе, проверим дату и размер
                long existingSize = cursor.getLong(0);
                long existingDate = cursor.getLong(1);

                if (existingSize != fileSize || existingDate != lastModified) {
                    String updateSql = "UPDATE files SET size = ?, last_modified = ? WHERE path = ?";
                    SQLiteStatement stmt = db.compileStatement(updateSql);
                    stmt.bindLong(1, fileSize);
                    stmt.bindLong(2, lastModified);
                    stmt.bindString(3, filePath);
                    stmt.executeUpdateDelete();
                    if (statCallback != null) {
                        statCallback.accept("updated", 1);
                    }
                    Profiler.get("metadata").record(System.currentTimeMillis() - start);
                    long contentStart = System.currentTimeMillis();
                    String content = new String(Files.readAllBytes(entry.file.toPath()), StandardCharsets.UTF_8);
                    Profiler.get("content").record(System.currentTimeMillis() - contentStart);
                    return content;
                } else {
                    if (statCallback != null) {
                        statCallback.accept("unchanged", 1);
                    }
                    Profiler.get("metadata").record(System.currentTimeMillis() - start);
                    return null;
                }
            } else {
                // Если файл новый, добавляем запись
                String insertSql = "INSERT INTO files (path, size, last_modified) VALUES (?, ?, ?)";
                SQLiteStatement stmt = db.compileStatement(insertSql);
                stmt.bindString(1, filePath);
                stmt.bindLong(2, fileSize);
                stmt.bindLong(3, lastModified);
                stmt.executeInsert();
                if (statCallback != null) {
                    statCallback.accept("added", 1);
                }
                Profiler.get("metadata").record(System.currentTimeMillis() - start);

                long contentStart = System.currentTimeMillis();
                String content = new String(Files.readAllBytes(entry.file.toPath()), StandardCharsets.UTF_8);
                Profiler.get("content").record(System.currentTimeMillis() - contentStart);
                return content;
            }
        } finally {
            cursor.close();
        }
    }

    public List<Map<String, String>> getAllFolders() {
        List<Map<String, String>> files = new ArrayList<>();

        Cursor cursor = db.rawQuery("SELECT id, path  FROM indexed_folders", null);
        try {
            if (cursor.moveToFirst()) {
                do {
                    Map<String, String> file = new HashMap<>();
                    file.put("id", cursor.getString(cursor.getColumnIndexOrThrow("id")));
                    file.put("path", cursor.getString(cursor.getColumnIndexOrThrow("path")));
                    files.add(file);
                } while (cursor.moveToNext());
            }
        } finally {
            cursor.close();
        }
        return files;
    }

    public List<String> getAllIndexedFolders() {
        List<String> paths = new ArrayList<>();
        Cursor cursor = db.rawQuery("SELECT path  FROM indexed_folders", null);
        try {
            if (cursor.moveToFirst()) {
                do {
                    paths.add(cursor.getString(cursor.getColumnIndexOrThrow("path")));
                } while (cursor.moveToNext());
            }
        } finally {
            cursor.close();
        }
        return paths;
    }

    public int getFileId(String filePath) {
        Cursor cursor = db.rawQuery("SELECT id FROM files WHERE path = ?", new String[]{filePath});
        try {
            if (cursor.moveToFirst()) {
                return cursor.getInt(0);
            } else {
                throw new RuntimeException("Файл не найден в таблице files: " + filePath);
            }
        } finally {
            cursor.close();
        }
    }

    public String getFilePath(int fileId) {
        Cursor cursor = db.rawQuery("SELECT path FROM files WHERE id = ?", new String[]{String.valueOf(fileId)});
        try {
            if (cursor.moveToFirst()) {
                return cursor.getString(0);
            } else {
                throw new RuntimeException("Файл не найден в таблице files: " + fileId);
            }
        } finally {
            cursor.close();
        }
    }

    public Map<Integer, List<Integer>> getFilesWithPositions(String token) {
        Map<Integer, List<Integer>> result = new HashMap<>();
        LogTimer total = new LogTimer(WebAppInterface.TAG, false);

//        Cursor cursor = db.rawQuery(
//        "SELECT t.file_id, t.positions_blob, f.path " +
//                "FROM tokens t " +
//                "JOIN dict d ON t.token_id = d.id " +
//                "JOIN files f ON t.file_id = f.id " +
//                "WHERE d.token = ? " +
//                "ORDER BY t.file_id",
//        new String[]{token});
        Cursor cursor = db.rawQuery(
                "SELECT t.file_id, t.positions_blob " +
                        "FROM tokens t " +
                        "JOIN dict d ON t.token_id = d.id " +
                        "WHERE d.token = ? ",
                new String[]{token});
        try {
            while (cursor.moveToNext()) {
                long tt = System.currentTimeMillis();
                int fileId = cursor.getInt(0);
                byte[] blob = cursor.getBlob(1);
                List<Integer> positions;
                positions = decodeDeltaVarIntBlob(blob);
                // Добавляем в Map — если ключ уже есть, объединяем списки
//                result.merge(fileId, positions, (oldList, newList) -> {
//                    oldList.addAll(newList);
//                    return oldList;
//                });
                result.merge(fileId, positions, (oldList, newList) -> mergeSortedLists(oldList, newList));
//                progressCallback.accept("search", new StatusUpdate("finished", t.getElapsed()));
                Profiler.get("collectPositions").record(System.currentTimeMillis() - tt);
            }
        } finally {
            cursor.close();
        }
        total.logTotal("DatabaseManager: result for token ready");
//        if (progressCallback != null) {
//            progressCallback.accept("search", new StatusUpdate("reading [" + token + "] from tokens finished", filesPos.getElapsed()));
//        }
        return result;
    }

    public void insertIndexedFolder(File folder) {
        String insertSql = "INSERT INTO indexed_folders (path) VALUES (?)";
        SQLiteStatement stmt = db.compileStatement(insertSql);
        stmt.bindString(1, folder.getAbsolutePath());
        stmt.executeInsert();
    }

    public void deleteIndexForPath (String prefix) {
        Log.d(WebAppInterface.TAG, "deleteIndexForPath called for folder: " + prefix);
        long startDeleting = System.currentTimeMillis();
        String safePrefix = prefix
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");

        String likeArg = safePrefix + "%";

        Cursor cursor = null;
        try {
            long filesTT = System.currentTimeMillis();
            int deletedFiles = db.delete(
                    "files",
                    "path LIKE ? ESCAPE '\\'",
                    new String[]{likeArg}
            );
            if (statCallback != null) {
                statCallback.accept("num of deleted files", deletedFiles);
            }
            Log.d(WebAppInterface.TAG, "Удалено из files: " + deletedFiles + " строк");
            Profiler.get("files").record(System.currentTimeMillis() - filesTT);

            long foldersTT = System.currentTimeMillis();
            int deletedFolders = db.delete(
                    "indexed_folders",
                    "path = ?",
                    new String[]{prefix}
            );
            if (statCallback != null) {
                statCallback.accept("num of deleted roots", deletedFolders);
            }
            Log.d(WebAppInterface.TAG, "Удалено из indexed_folders: " + deletedFolders + " строк");
            Profiler.get("folders").record(System.currentTimeMillis() - foldersTT);
            Profiler.get("deleting").record(System.currentTimeMillis() - startDeleting);
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    public void deleteOrphanTerms() {
//        int deleted = db.compileStatement(
//                "DELETE FROM dict WHERE id NOT IN (SELECT DISTINCT token_id FROM tokens)"
//        ).executeUpdateDelete();
        int deleted = db.compileStatement(
                "DELETE FROM dict\n" +
                        "WHERE NOT EXISTS (\n" +
                        "    SELECT 1 FROM tokens WHERE tokens.token_id = dict.id\n" +
                        ");"
        ).executeUpdateDelete();
        if (statCallback != null) {
            statCallback.accept("num of deleted terms", deleted);
        }
    }

    public void deleteMissingFilesFromDb(Set<String> pathsOfExistingResources, File[] rootPathsFromClient) {
        LogTimer delMissing = new LogTimer(true);
        if (progressCallback != null) {
            progressCallback.accept("statusUpdate", new StatusUpdate("→ Deleting missing files..."));
        }
        int deleted = 0;
        for (File root : rootPathsFromClient) {
            String prefix = root.getAbsolutePath();
            String safePrefix = prefix
                    .replace("\\", "\\\\")
                    .replace("%", "\\%")
                    .replace("_", "\\_");
            String likeArg = safePrefix + "%";

            Cursor cursor = null;
            try {
                cursor = db.rawQuery(
                        "SELECT path FROM files WHERE path LIKE ? ESCAPE '\\'",
                        new String[]{likeArg}
                );

                List<String> toDelete = new ArrayList<>();
                while (cursor.moveToNext()) {
                    String pathInDb = cursor.getString(0);
                    if (!pathsOfExistingResources.contains(pathInDb)) {
                        toDelete.add(pathInDb);
                    }
                }
                cursor.close();

                for (String path : toDelete) {
                    db.delete("files", "path = ?", new String[]{path});
                    deleted++;
                }

            } finally {
                if (cursor != null) cursor.close();
            }
        }
        if (progressCallback != null) {
            progressCallback.accept("statusUpdate", new StatusUpdate(deleted + " deleted", delMissing.getElapsed()));
        }
    }

    public boolean rootExists(File folder) {
        Cursor cursor = db.rawQuery("SELECT id  FROM indexed_folders WHERE path = ?", new String[]{folder.getAbsolutePath()});
        try {
            return cursor.moveToFirst();
        } finally {
            cursor.close();
        }
    }

    private List<Integer> decodeDeltaVarIntBlob(byte[] blob) {
        List<Integer> positions = new ArrayList<>();
        int pos = 0;
        int i = 0;
        while (i < blob.length) {
            int shift = 0;
            int result = 0;
            while (true) {
                int b = blob[i++] & 0xFF;
                result |= (b & 0x7F) << shift;
                if ((b & 0x80) == 0) break;
                shift += 7;
            }
            pos += result;
            positions.add(pos);
        }
        return positions;
    }
}