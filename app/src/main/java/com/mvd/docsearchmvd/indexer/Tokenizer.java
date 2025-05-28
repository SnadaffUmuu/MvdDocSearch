package com.mvd.docsearchmvd.indexer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import com.mvd.docsearchmvd.WebAppInterface;
import com.mvd.docsearchmvd.model.Token;
import com.mvd.docsearchmvd.util.Profiler;
import com.mvd.docsearchmvd.util.Util;

import android.util.Log;

public class Tokenizer {
    private static final Pattern PUNCTUATION_OR_SYMBOL = Pattern.compile( "[\\p{P}\\p{S}\\p{N}\\p{Z}\\p{C}ー々〆〤]");
    private static final Pattern EMOJI = Pattern.compile("[\\uD83C-\\uDBFF\\uDC00-\\uDFFF]+");

    public Tokenizer() {
    }
    public List<Token> tokenize(String text) {
        Log.d(WebAppInterface.TAG, "Start tokenize method");
        long timerStart = System.currentTimeMillis();
        Map<String, IntList> tokenMap = new LinkedHashMap<>();

        char[] chars = text.toCharArray();
        int pos = 0;
        while (pos < chars.length) {
            char ch = chars[pos];
            String chStr = String.valueOf(ch);

            if (Character.isWhitespace(ch)
                    || PUNCTUATION_OR_SYMBOL.matcher(chStr).matches()
                    || EMOJI.matcher(chStr).matches()
                    || Character.isDigit(ch)) {
                pos++;
                continue;
            }

            int start = pos;
            boolean isWord = Util.isAsciiLetterOrCyrillicOrDigit(ch);

            if (isWord) {
                while (pos < chars.length && Util.isAsciiLetterOrCyrillicOrDigit(chars[pos])) {
                    pos++;
                }
                if (pos - start == 1) {
                    pos++; // пропустить этот символ
                    continue; // не добавлять однобуквенные латиницу/кириллицу
                }
            } else {
                pos++;
            }

            String tokenStr = new String(chars, start, pos - start);
            if (isWord) {
                tokenStr = tokenStr.toLowerCase(Locale.ROOT);
            }
            tokenMap.computeIfAbsent(tokenStr, k -> new IntList()).add(start);
        }

        List<Token> tokens = new ArrayList<>(tokenMap.size());
        for (Map.Entry<String, IntList> entry : tokenMap.entrySet()) {
            IntList positions = entry.getValue();
            if (positions.size() == 0) continue;
            positions.sort();
            byte[] blob = toDeltaVarIntBlob(positions);
            if (blob.length == 0) continue; // Исключаем токены с пустым positionsBlob
            tokens.add(new Token(entry.getKey(), blob));
        }
        Profiler.get("tokenize").record(System.currentTimeMillis() - timerStart);
        return tokens;
    }

    private static byte[] toDeltaVarIntBlob(IntList positions) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int prev = 0;
        for (int i = 0; i < positions.size(); i++) {
            int delta = positions.get(i) - prev;
            prev = positions.get(i);
            writeVarInt(out, delta);
        }
        return out.toByteArray();
    }

    private static void writeVarInt(OutputStream out, int value) {
        while ((value & ~0x7F) != 0) {
            try {
                out.write((value & 0x7F) | 0x80);
                value >>>= 7;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        try {
            out.write(value);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static class IntList {
        private int[] data = new int[8];
        private int size = 0;

        void sort() {
            Arrays.sort(data, 0, size);
        }

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
}

