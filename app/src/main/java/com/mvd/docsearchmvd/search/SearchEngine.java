package com.mvd.docsearchmvd.search;

import com.mvd.docsearchmvd.WebAppInterface;
import com.mvd.docsearchmvd.db.DatabaseManager;

import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;
import static com.mvd.docsearchmvd.util.Util.isAsciiLetterOrCyrillicOrDigit;

import android.util.Log;

public class SearchEngine {
    private DatabaseManager db;

    public SearchEngine(DatabaseManager db) {
        this.db = db;
    }

    public List<Hit> search(String initialQuery) throws SQLException {

        Log.d(WebAppInterface.TAG, "Searching text: " + initialQuery);

        String query = initialQuery.toLowerCase(Locale.ROOT);

        List<String> queryTokens = new ArrayList<>();
        int pos = 0;
        while (pos < query.length()) {
            char ch = query.charAt(pos);
            if (Character.isWhitespace(ch)) {
                pos++;
                continue;
            }
            int start = pos;
            if (isAsciiLetterOrCyrillicOrDigit(ch)) {
                while (pos < query.length() &&
                        (isAsciiLetterOrCyrillicOrDigit(query.charAt(pos)))) {
                    pos++;
                }
            } else {
                pos++;
            }
            String tokenStr = query.substring(start, pos);
            queryTokens.add(tokenStr);
        }

        List<Map<Integer, List<Integer>>> postings = new ArrayList<>();

        for (String token : queryTokens) {
            postings.add(db.getFilesWithPositions(token));
        }

        Set<Integer> commonFiles = new HashSet<>(postings.get(0).keySet());

        for (int i = 1; i < postings.size(); i++) {
            commonFiles.retainAll(postings.get(i).keySet());
        }

        List<Hit> result = new ArrayList<>();

        for (int fileId : commonFiles) {
            Set<Integer> base = new HashSet<>(postings.get(0).get(fileId));
            boolean matched = true;

            for (int i = 1; i < queryTokens.size(); i++) {
                List<Integer> positions = postings.get(i).get(fileId);
                if (positions == null) {
                    matched = false;
                    break;
                }

                int finalI = i;
                Set<Integer> shifted = positions.stream()
                        .map(p -> p - finalI)
                        .collect(Collectors.toSet());

                base.retainAll(shifted);
                if (base.isEmpty()) {
                    matched = false;
                    break;
                }
            }

            if (matched) {
                int hitCount = base.size();
                String path = db.getFilePath(fileId);
                result.add(new Hit(fileId, path, hitCount));
            }
        }

        return result;
    }
}
