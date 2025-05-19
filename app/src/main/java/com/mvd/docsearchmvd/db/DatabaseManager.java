package com.mvd.docsearchmvd.db;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.util.Log;

import com.mvd.docsearchmvd.WebAppInterface;
import com.mvd.docsearchmvd.indexer.FileIndexer;
import com.mvd.docsearchmvd.util.LogTimer;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

public class DatabaseManager {
    private final SQLiteDatabase db;
    private final Context context;

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
                    "positions TEXT NOT NULL, " +
                    "FOREIGN KEY(file_id) REFERENCES files(id) ON DELETE CASCADE, " +
                    "FOREIGN KEY(token_id) REFERENCES dict(id));");

            db.execSQL("CREATE INDEX IF NOT EXISTS idx_tokens_token_id " +
                    "ON tokens(token_id)");

//            Log.d(WebAppInterface.TAG, "create indexed_folders if not exists");
            db.execSQL("CREATE TABLE  IF NOT EXISTS indexed_folders(" +
                    "id INTEGER PRIMARY KEY, " +
                    "path TEXT NOT NULL UNIQUE);");

            db.execSQL("CREATE INDEX IF NOT EXISTS idx_files_path " +
                            "ON files(path)"
            );

            db.execSQL("CREATE INDEX IF NOT EXISTS idx_dict_token ON dict(token);");

            db.execSQL("CREATE INDEX IF NOT EXISTS idx_tokens_token_id_file_id ON tokens(token_id, file_id);");

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
        LogTimer total = new LogTimer(WebAppInterface.TAG, false);
        total.log("SearchEngine: clearTables started");
        db.execSQL("DELETE FROM files;");
        db.execSQL("DELETE FROM dict;");
        if (!keepRoots) {
            db.execSQL("DELETE FROM indexed_folders;");
        }
        total.log("SearchEngine: clearing tables finished");
    }

    public String updateFileMetadata(File file) throws IOException {
        Log.d(WebAppInterface.TAG, "updateFileMetadata");
        String filePath = file.getAbsolutePath();
        long fileSize = file.length();
        long lastModified = file.lastModified();

        Cursor cursor = db.rawQuery("SELECT size, last_modified FROM files WHERE path = ?", new String[]{filePath});

        try {
            if (cursor.moveToFirst()) {
                // Если файл уже есть в базе, проверим дату и размер
//                Log.d(WebAppInterface.TAG, "file exists");
                long existingSize = cursor.getLong(0);
                long existingDate = cursor.getLong(1);

                if (existingSize != fileSize || existingDate != lastModified) {
                    // Если размер или дата изменились, обновляем запись
//                    Log.d(WebAppInterface.TAG, "file changed, updating DB");
                    String updateSql = "UPDATE files SET size = ?, last_modified = ? WHERE path = ?";
                    SQLiteStatement stmt = db.compileStatement(updateSql);
                    stmt.bindLong(1, fileSize);
                    stmt.bindLong(2, lastModified);
                    stmt.bindString(3, filePath);
                    stmt.executeUpdateDelete();
//                    Log.d(WebAppInterface.TAG, "[INFO] Обновление файла: " + filePath);
                    return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                } else {
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

//                Log.d(WebAppInterface.TAG, "[INFO] Новый файл: " + filePath);
//                Log.d(WebAppInterface.TAG, "Try to read the content now...");
                return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
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

    public Map<Integer, List<Integer>> getFilesWithPositions(String query) {

        LogTimer total = new LogTimer(WebAppInterface.TAG, false);
        Map<Integer, List<Integer>> result = new HashMap<>();
        Cursor cursor = db.rawQuery(
        "SELECT t.file_id, t.positions, f.path " +
                "FROM tokens t " +
                "JOIN dict d ON t.token_id = d.id " +
                "JOIN files f ON t.file_id = f.id " +
                "WHERE d.token = ? " +
                "ORDER BY t.file_id",
        new String[]{query});
        int i = 0;
        try {
            while (cursor.moveToNext()) {
                int fileId = cursor.getInt(0);
                String posString = cursor.getString(1);
                // Разделяем строку позиций по запятой и преобразуем в числа
                String[] splittedPostings = posString.split(",");
                List<Integer> positions = new ArrayList<>(splittedPostings.length);
                //LogTimer tt = new LogTimer(WebAppInterface.TAG, true);
                for (String part : splittedPostings) {
                    positions.add(Integer.parseInt(part.trim()));
                }
                //tt.log("positions split and parsed as Integer");
                // Добавляем в Map — если ключ уже есть, объединяем списки
                result.merge(fileId, positions, (oldList, newList) -> {
                    oldList.addAll(newList);
                    return oldList;
                });
            }
        } finally {
            cursor.close();
        }
        total.log("DatabaseManager: result for query ready");
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
        String safePrefix = prefix
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");

        String likeArg = safePrefix + "%";

        Cursor cursor = null;
        try {
            cursor = db.rawQuery(
                    "SELECT COUNT(*) FROM files WHERE path LIKE ? ESCAPE '\\'",
                    new String[]{likeArg}
            );
            if (cursor.moveToFirst()) {
                int count = cursor.getInt(0);
                Log.d(WebAppInterface.TAG, "Найдено для удаления из files: " + count + " записей");
            }

            int deletedFiles = db.delete(
                    "files",
                    "path LIKE ? ESCAPE '\\'",
                    new String[]{likeArg}
            );
            Log.d(WebAppInterface.TAG, "Удалено из files: " + deletedFiles + " строк");

            // Удаление неиспользуемых токенов
            Log.d(WebAppInterface.TAG, "Удаляем неиспользуемые токены из dict...");
            db.execSQL("DELETE FROM dict " +
                    "WHERE NOT EXISTS (" +
                    "    SELECT 1 FROM tokens " +
                    "    WHERE tokens.token_id = dict.id" +
                    ")");

            int deletedFolders = db.delete(
                    "indexed_folders",
                    "path = ?",
                    new String[]{prefix}
            );
            Log.d(WebAppInterface.TAG, "Удалено из indexed_folders: " + deletedFolders + " строк");
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    public void deleteMissingFilesFromDb(Set<String> diskFilePaths, File[] roots) {
        for (File root : roots) {
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
                    if (!diskFilePaths.contains(pathInDb)) {
                        toDelete.add(pathInDb);
                    }
                }
                cursor.close();

                for (String path : toDelete) {
                    db.delete("files", "path = ?", new String[]{path});
                }

                // Чистим осиротевшие токены
                db.execSQL("DELETE FROM dict WHERE id NOT IN (SELECT DISTINCT token_id FROM tokens)");

            } finally {
                if (cursor != null) cursor.close();
            }
        }
    }

    public boolean rootExists(File folder) {
//        Log.d(WebAppInterface.TAG, "rootExists called for folder: " + folder.getAbsolutePath());
        Cursor cursor = db.rawQuery("SELECT id  FROM indexed_folders WHERE path = ?", new String[]{folder.getAbsolutePath()});
        try {
            return cursor.moveToFirst();
        } finally {
            cursor.close();
        }
    }
}