package com.mvd.docsearchmvd.indexer;

import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
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
            String token = entry.getKey();
            IntList positions = entry.getValue();
            positions.sort();

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

