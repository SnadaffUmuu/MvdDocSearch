package com.mvd.docsearchmvd.indexer;

import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.mvd.docsearchmvd.WebAppInterface;
import com.mvd.docsearchmvd.db.TokenDictionary;
import com.mvd.docsearchmvd.util.Util;

public class Tokenizer {

    private static final Pattern PUNCTUATION_OR_SYMBOL = Pattern.compile("[\\p{P}\\p{So}ー々〆〤\u3000\u3001\u3002\u300C\u300D\u300E\u300F]");
    private static final Pattern EMOJI = Pattern.compile("[\\uD83C-\\uDBFF\\uDC00-\\uDFFF]+");

    public Tokenizer() {
    }

    public List<Token> tokenize(String text) {
        Map<String, IntList> tokenMap = new LinkedHashMap<>();

        char[] chars = text.toCharArray();
        int pos = 0;
        while (pos < chars.length) {
            char ch = chars[pos];
            String chStr = String.valueOf(ch);

            if (Character.isWhitespace(ch)
                    || PUNCTUATION_OR_SYMBOL.matcher(chStr).matches()
                    || EMOJI.matcher(chStr).matches()) {
                pos++;
                continue;
            }

            int start = pos;

            if (Util.isAsciiLetterOrCyrillicOrDigit(ch)) {
                while (pos < chars.length && Util.isAsciiLetterOrCyrillicOrDigit(chars[pos])) {
                    pos++;
                }
            } else {
                pos++;
            }

            String tokenStr = new String(chars, start, pos - start);
            tokenMap.computeIfAbsent(tokenStr, k -> new IntList()).add(start);
        }

        // Формируем финальный список токенов
        List<Token> tokens = new ArrayList<>(tokenMap.size());
        for (Map.Entry<String, IntList> entry : tokenMap.entrySet()) {
            String token = entry.getKey();
            IntList positions = entry.getValue();

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < positions.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append(positions.get(i));
            }

            tokens.add(new Token(token, sb.toString()));
        }

        return tokens;
    }

    private static class IntList {
        private int[] data = new int[8];
        private int size = 0;

        void add(int value) {
            if (size == data.length) {
                data = Arrays.copyOf(data, size * 2);
            }
            data[size++] = value;
        }

        int get(int index) {
            return data[index];
        }

        int size() {
            return size;
        }
    }

    public void tokenizeAndInsert(String text, int fileId, SQLiteDatabase conn, TokenDictionary dict) {
        final Pattern punctuationPattern = PUNCTUATION_OR_SYMBOL;
        final Pattern emojiPattern = EMOJI;

        Map<String, IntList> tokenMap = new LinkedHashMap<>();

        char[] chars = text.toCharArray();
        int pos = 0;

        // Открываем SQL-подготовку заранее
        String sql = "INSERT INTO tokens (token_id, file_id, positions) VALUES (?, ?, ?)";
        SQLiteStatement stmt = conn.compileStatement(sql);

        while (pos < chars.length) {
            char ch = chars[pos];
            String chStr = String.valueOf(ch);

            if (Character.isWhitespace(ch) ||
                    punctuationPattern.matcher(chStr).matches() ||
                    emojiPattern.matcher(chStr).matches()) {
                pos++;
                continue;
            }

            int start = pos;
            if (Util.isAsciiLetterOrCyrillicOrDigit(ch)) {
                while (pos < chars.length && Util.isAsciiLetterOrCyrillicOrDigit(chars[pos])) {
                    pos++;
                }
            } else {
                pos++;
            }

            String tokenStr = new String(chars, start, pos - start);
            IntList positions = tokenMap.computeIfAbsent(tokenStr, k -> new IntList());
            positions.add(start);
        }

        try {
            for (Map.Entry<String, IntList> entry : tokenMap.entrySet()) {
                String tokenText = entry.getKey();
                IntList positions = entry.getValue();

                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < positions.size(); i++) {
                    if (i > 0) sb.append(',');
                    sb.append(positions.get(i));
                }

                int tokenId = dict.getTokenId(tokenText);
                stmt.clearBindings();
                stmt.bindLong(1, tokenId);
                stmt.bindLong(2, fileId);
                stmt.bindString(3, sb.toString());
                stmt.executeInsert();
            }
        } catch(Exception e) {
            Log.e(WebAppInterface.TAG, "Ошибка в процессе токенизации", e);
        } finally {
            stmt.close();
        }
    }

    private void insertToken(SQLiteStatement stmt, TokenDictionary dict, String token, int fileId, String positions) {
        try {
            int tokenId = dict.getTokenId(token);
            stmt.clearBindings();
            stmt.bindLong(1, tokenId);
            stmt.bindLong(2, fileId);
            stmt.bindString(3, positions);
            stmt.executeInsert();
        } catch (Exception e) {
            Log.e(WebAppInterface.TAG, "Ошибка вставки токена: " + token, e);
        }
    }

    public void tokenizeAndInsertStreaming(String text, int fileId, SQLiteDatabase conn, TokenDictionary dict) {
        final Pattern punctuationPattern = PUNCTUATION_OR_SYMBOL;
        final Pattern emojiPattern = EMOJI;

        char[] chars = text.toCharArray();
        int pos = 0;

        String lastToken = null;
        StringBuilder positions = new StringBuilder();

        String sql = "INSERT INTO tokens (token_id, file_id, positions) VALUES (?, ?, ?)";
        SQLiteStatement stmt = conn.compileStatement(sql);

        try {
            while (pos < chars.length) {
                char ch = chars[pos];
                String chStr = String.valueOf(ch);

                if (Character.isWhitespace(ch) ||
                        punctuationPattern.matcher(chStr).matches() ||
                        emojiPattern.matcher(chStr).matches()) {
                    pos++;
                    continue;
                }

                int start = pos;
                if (Util.isAsciiLetterOrCyrillicOrDigit(ch)) {
                    while (pos < chars.length && Util.isAsciiLetterOrCyrillicOrDigit(chars[pos])) {
                        pos++;
                    }
                } else {
                    pos++;
                }

                String tokenStr = new String(chars, start, pos - start);

                if (lastToken == null) {
                    // Первый токен
                    lastToken = tokenStr;
                    positions.append(start);
                } else if (lastToken.equals(tokenStr)) {
                    // Тот же токен — накапливаем позицию
                    positions.append(',').append(start);
                } else {
                    // Новый токен — сброс предыдущего
                    insertToken(stmt, dict, lastToken, fileId, positions.toString());

                    // Новый токен и его первая позиция
                    lastToken = tokenStr;
                    positions.setLength(0);
                    positions.append(start);
                }
            }

            // Финальный сброс
            if (lastToken != null && positions.length() > 0) {
                insertToken(stmt, dict, lastToken, fileId, positions.toString());
            }

        } finally {
            stmt.close();
        }
    }
}

