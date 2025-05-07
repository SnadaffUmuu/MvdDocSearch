package com.mvd.docsearchmvd.indexer;

import android.util.Log;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.mvd.docsearchmvd.WebAppInterface;
import com.mvd.docsearchmvd.util.Util;

public class Tokenizer {

    public Tokenizer() {
    }

    public List<Token> tokenize(String text) {
        Log.d(WebAppInterface.TAG, "tokenize");
        Map<String, List<Integer>> tokenMap = new LinkedHashMap<>();
        int pos = 0;
        while (pos < text.length()) {
            char ch = text.charAt(pos);
            if (Character.isWhitespace(ch)
                    || String.valueOf(ch).matches("[\\p{P}\\p{So}ー々〆〤\u3000\u3001\u3002\u300C\u300D\u300E\u300F]") ||
                    String.valueOf(ch).matches("[\\uD83C-\\uDBFF\\uDC00-\\uDFFF]+")) {
                pos++;
                continue;
            }
            int start = pos;

            if (Util.isAsciiLetterOrCyrillicOrDigit(ch)) {
                while (pos < text.length() &&
                        (Util.isAsciiLetterOrCyrillicOrDigit(text.charAt(pos)))) {
                    pos++;
                }
            } else {
                pos++;
            }
            String tokenStr = text.substring(start, pos);

            tokenMap
                    .computeIfAbsent(tokenStr, k -> new ArrayList<>())
                    .add(start);

        }

        List<Token> tokens = new ArrayList<>();
        for (Map.Entry<String, List<Integer>> entry : tokenMap.entrySet()) {
            String token = entry.getKey();
            String positions = entry.getValue()
                    .stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));
            tokens.add(new Token(token, positions));
        }

        return tokens;
    }

}

