package com.mvd.docsearchmvd.db;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.util.Log;

import androidx.documentfile.provider.DocumentFile;

import com.mvd.docsearchmvd.WebAppInterface;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class DatabaseManager {
    private final SQLiteDatabase db;
    private final Context context;

    public DatabaseManager(Context context) {
        this.context = context;
        String dbPath = context.getFilesDir().getAbsolutePath() + "/index.db";
        this.db = SQLiteDatabase.openOrCreateDatabase(dbPath, null);
    }

    public SQLiteDatabase getConnection() {
        return db;
    }

    public void init(boolean clear) {
        Log.d(WebAppInterface.TAG, "Init db");

        db.execSQL("CREATE TABLE IF NOT EXISTS dict (id INTEGER PRIMARY KEY, token TEXT UNIQUE);");

        db.execSQL("CREATE TABLE IF NOT EXISTS files(" +
                "id INTEGER PRIMARY KEY, " +
                "path TEXT UNIQUE NOT NULL, " +
                "display_path TEXT NOT NULL, " +
                "size INTEGER NOT NULL, " +
                "last_modified INTEGER NOT NULL);");

        db.execSQL("CREATE TABLE IF NOT EXISTS tokens(" +
                "token_id INTEGER, " +
                "file_id INTEGER NOT NULL REFERENCES files(id), " +
                "positions TEXT NOT NULL, " +
                "FOREIGN KEY(token_id) REFERENCES dict(id));");

                if (clear) {
                    db.execSQL("DELETE FROM tokens;");
                    db.execSQL("DELETE FROM files;");
                    db.execSQL("DELETE FROM dict;");
                    Log.d(WebAppInterface.TAG, "[INFO] База очищена");
                }
    }

    public String updateFileMetadata(DocumentFile file) throws IOException {
        Log.d(WebAppInterface.TAG, "updateFileMetadata");
        String filePath = file.getUri().toString();
        String displayPath = buildRelativePath(file);
        long fileSize = file.length();
        long lastModified = file.lastModified();

        Cursor cursor = db.rawQuery("SELECT size, last_modified FROM files WHERE path = ?", new String[]{filePath});

        try {
            if (cursor.moveToFirst()) {
                // Если файл уже есть в базе, проверим дату и размер
                Log.d(WebAppInterface.TAG, "file exists");
                long existingSize = cursor.getLong(0);
                long existingDate = cursor.getLong(1);

                if (existingSize != fileSize || existingDate != lastModified) {
                    // Если размер или дата изменились, обновляем запись
                    Log.d(WebAppInterface.TAG, "file changed, updating DB");
                    String updateSql = "UPDATE files SET size = ?, last_modified = ? WHERE path = ?";
                    SQLiteStatement stmt = db.compileStatement(updateSql);
                    stmt.bindLong(1, fileSize);
                    stmt.bindLong(2, lastModified);
                    stmt.bindString(3, filePath);
                    stmt.executeUpdateDelete();
                    Log.d(WebAppInterface.TAG, "[INFO] Обновление файла: " + displayPath);
                    return readFileContent(file);
                } else {
                    return null;
                }
            } else {
                // Если файл новый, добавляем запись
                String insertSql = "INSERT INTO files (path, display_path, size, last_modified) VALUES (?, ?, ?, ?)";
                SQLiteStatement stmt = db.compileStatement(insertSql);
                stmt.bindString(1, filePath);
                stmt.bindString(2, displayPath);
                stmt.bindLong(3, fileSize);
                stmt.bindLong(4, lastModified);
                stmt.executeInsert();

                Log.d(WebAppInterface.TAG, "[INFO] Новый файл: " + displayPath);
                return readFileContent(file);
            }
        } finally {
            cursor.close();
        }
        //TODO: remove missing files from db
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

    public Map<Integer, List<Integer>> getFilesWithPositions(String query) {
        Map<Integer, List<Integer>> result = new HashMap<>();
        Cursor cursor = db.rawQuery(
                "SELECT t.file_id, t.positions " +
                        "FROM tokens t JOIN dict d ON t.token_id = d.id " +
                        "WHERE d.token = ? ORDER BY t.file_id",
                new String[]{query});

        try {
            while (cursor.moveToNext()) {
                int fileId = cursor.getInt(0);
                String posString = cursor.getString(1);
                // Разделяем строку позиций по запятой и преобразуем в числа
                List<Integer> positions = new ArrayList<>();
                for (String part : posString.split(",")) {
                    positions.add(Integer.parseInt(part.trim()));
                }
                // Добавляем в Map — если ключ уже есть, объединяем списки
                result.merge(fileId, positions, (oldList, newList) -> {
                    oldList.addAll(newList);
                    return oldList;
                });
            }
        } finally {
            cursor.close();
        }
        return result;
    }

    private boolean indexNotExists(String indexName) {
        Cursor cursor = db.rawQuery(
                "SELECT 1 FROM sqlite_master WHERE type = 'index' AND name = ?",
                new String[]{indexName});
        try {
            return !cursor.moveToFirst();
        } finally {
            cursor.close();
        }
    }

    private String buildRelativePath(DocumentFile file) {
        List<String> parts = new ArrayList<>();
        DocumentFile current = file;
        while (current != null && current.getName() != null) {
            parts.add(current.getName());
            current = current.getParentFile();  // Может вернуть null
        }
        Collections.reverse(parts);
        return String.join("/", parts);  // → Review/vocab/sample.txt
    }

    private String readFileContent(DocumentFile file) throws IOException {
        InputStream in = context.getContentResolver().openInputStream(file.getUri());
        if (in == null) throw new IOException("Cannot open input stream for file: " + file.getUri());
        byte[] bytes = readAllBytes(in);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private byte[] readAllBytes(InputStream input) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[4096];
        int nRead;
        while ((nRead = input.read(data)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return buffer.toByteArray();
    }
}