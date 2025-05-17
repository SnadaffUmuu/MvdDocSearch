package com.mvd.docsearchmvd.search;

import com.mvd.docsearchmvd.WebAppInterface;
import com.mvd.docsearchmvd.db.DatabaseManager;
import com.mvd.docsearchmvd.util.LogTimer;

import java.io.File;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;
import static com.mvd.docsearchmvd.util.Util.isAsciiLetterOrCyrillicOrDigit;

public class SearchEngine {
    private DatabaseManager db;

    public SearchEngine(DatabaseManager db) {
        this.db = db;
    }

    public List<Hit> search(String initialQuery) throws SQLException {
        LogTimer total = new LogTimer(WebAppInterface.TAG, false);
        total.log("SearchEngine: starting search" + initialQuery);
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
            Map<Integer, List<Integer>> tokenPostings = db.getFilesWithPositions(token);
            postings.add(tokenPostings);
        }

        Set<Integer> commonFiles = new HashSet<>(postings.get(0).keySet());

        for (int i = 1; i < postings.size(); i++) {
            commonFiles.retainAll(postings.get(i).keySet());
        }

        List<Hit> result = new ArrayList<>();

        for (int fileId : commonFiles) {
            LogTimer tt = new LogTimer(WebAppInterface.TAG, true);
            List<Integer> base = postings.get(0).get(fileId);
            if (base == null || base.isEmpty()) continue;
            List<Integer> intersection = new ArrayList<>(base); // копия для пересечений
            boolean matched = true;
            for (int i = 1; i < queryTokens.size(); i++) {
                List<Integer> next = postings.get(i).get(fileId);
                if (next == null || next.isEmpty()) {
                    matched = false;
                    break;
                }

                List<Integer> temp = new ArrayList<>();
                int a = 0, b = 0, offset = i;

                while (a < intersection.size() && b < next.size()) {
                    int va = intersection.get(a);
                    int vb = next.get(b) - offset;

                    if (va == vb) {
                        temp.add(va);
                        a++;
                        b++;
                    } else if (va < vb) {
                        a++;
                    } else {
                        b++;
                    }
                }

                if (temp.isEmpty()) {
                    matched = false;
                    break;
                }

                intersection = temp;
            }
            tt.log("SearchEngine: pos matching finished");

            if (matched) {
                int hitCount = intersection.size();
                String path = db.getFilePath(fileId);
                File file = new File(path);
                if (file.exists()) {
                    result.add(new Hit(fileId, path, hitCount));
                }
            }
        }
        total.log("SearchEngine: search finished, found: " + result.size());
        return result;
    }
}
