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

    public FileIndexer(DatabaseManager db, Context context) throws SQLException {
        this.context = context;
        this.conn = db.getConnection();
        this.db = db;
        this.tokenizer = new Tokenizer();
        this.dict = new TokenDictionary(conn);
    }

    private boolean isAllowedExtension(DocumentFile file) {
        String name = file.getName().toLowerCase();
        return Arrays.stream(extensions).anyMatch(name::endsWith);
    }

    private void indexFile(DocumentFile file, String content) {
        Log.d(WebAppInterface.TAG, "indexFile");

        int fileId;
        fileId = db.getFileId(file.getUri().toString());

        List<Token> tokens = tokenizer.tokenize(content);

        String sql = "INSERT INTO tokens (token_id, file_id, positions) VALUES (?, ?, ?)";
        SQLiteStatement stmt = conn.compileStatement(sql);

        for (Token token : tokens) {
            try {
                int tokenId = dict.getTokenId(token.text);
                stmt.clearBindings();
                stmt.bindLong(1, tokenId);
                stmt.bindLong(2, fileId);
                stmt.bindString(3, token.positions);
                stmt.executeInsert();  // вставка одной строки
            } catch (Exception e) {
                Log.e(WebAppInterface.TAG, "Ошибка вставки токена: " + token.text, e);
            }
        }

        stmt.close();
    }

    public void indexFileIfNeeded(DocumentFile file) throws SQLException, IOException {
        Log.d(WebAppInterface.TAG, "indexFileIfNeeded");
        String content = db.updateFileMetadata(file);
        if (content != null) {
            indexFile(file, content);
        }
    }

    public void indexFilesInDirectory(DocumentFile folder) throws SQLException, IOException {
        Log.d(WebAppInterface.TAG, "indexFilesInDirectory");
        Log.d(WebAppInterface.TAG, folder.getName());
        if (folder.isDirectory()) {
            DocumentFile[] files = folder.listFiles();
            if (files == null) return;
            for (DocumentFile file : files) {
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

    public void updateIndex(DocumentFile[] folders) throws IOException, SQLException {
        Log.d(WebAppInterface.TAG, "updateIndex");
        conn.beginTransaction();
        try {
            for (DocumentFile dir : folders) {
                Log.d(WebAppInterface.TAG, dir.getUri().toString());
                indexFilesInDirectory(dir);
            }
            conn.setTransactionSuccessful(); // Помечаем транзакцию как успешную
        } catch (Exception e) {
            Log.e(WebAppInterface.TAG, "Ошибка при обновлении индекса", e);
            throw e; // Прокидываем исключение, чтобы вызвать откат
        } finally {
            conn.endTransaction(); // Закрывает транзакцию, коммит или откат в зависимости от успешности
        }
    }
}
