package com.mvd.docsearchmvd.db;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import java.sql.*;
import java.util.LinkedHashMap;
import java.util.Map;

public class TokenDictionary {
    private final SQLiteDatabase conn;

    // Мини-кеш на 1000 элементов
    private final LinkedHashMap<String, Integer> cache = new LinkedHashMap<String, Integer>(1000, 0.75f, true) {
        protected boolean removeEldestEntry(Map.Entry<String, Integer> eldest) {
            return size() > 1000;
        }
    };

    public TokenDictionary(SQLiteDatabase conn) throws SQLException {
        this.conn = conn;
    }

    public int getTokenId(String token) throws SQLException {
        if (cache.containsKey(token)) {
            return cache.get(token);
        }

        // 1. Попробовать найти
        Cursor cursor = conn.rawQuery("SELECT id FROM dict WHERE token = ?", new String[]{token});
        if (cursor.moveToFirst()) {
            int id = cursor.getInt(0);
            cursor.close();
            cache.put(token, id);
            return id;
        }
        cursor.close();

        // 2. Вставить
        ContentValues values = new ContentValues();
        values.put("token", token);
        long rowId = conn.insert("dict", null, values);
        if (rowId == -1) {
            throw new SQLiteException("Failed to insert token: " + token);
        }

        int id = (int) rowId; // SQLite возвращает ROWID, совпадающий с id, если id INTEGER PRIMARY KEY
        cache.put(token, id);
        return id;
    }
}
