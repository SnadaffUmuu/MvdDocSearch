package com.mvd.docsearchmvd.search;

import com.mvd.docsearchmvd.db.DatabaseManager;
import com.mvd.docsearchmvd.model.Hit;
import com.mvd.docsearchmvd.model.StatusUpdate;
import com.mvd.docsearchmvd.util.LogTimer;
import com.mvd.docsearchmvd.util.Profiler;

import java.io.File;
import java.sql.SQLException;
import java.util.*;
import java.util.function.BiConsumer;

import static com.mvd.docsearchmvd.util.Util.isAsciiLetterOrCyrillicOrDigit;

public class SearchEngine {
    private DatabaseManager db;

    private BiConsumer<String, Object> progressCallback;

    public void setProgressCallback(BiConsumer<String, Object> callback) {
        this.progressCallback = callback;
    }

    public SearchEngine(DatabaseManager db) {
        this.db = db;
    }

    public List<Hit> search(String initialQuery) throws SQLException {
        LogTimer tokenizeQuery = new LogTimer(true);
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
        if (progressCallback != null) {
            progressCallback.accept("search", new StatusUpdate("Tokenized query (" + queryTokens.size() + " tokens)", tokenizeQuery.getElapsed()));
        }

        LogTimer collectPostings = new LogTimer(false);

        List<Map<Integer, List<Integer>>> postings = new ArrayList<>();

        Map<String, Integer> stats = new HashMap<>();

        db.setStatCallback((type, num) -> {
            stats.merge(type, (Integer) num, Integer::sum);
        });

        for (String token : queryTokens) {
            Map<Integer, List<Integer>> tokenPostings = db.getFilesWithPositions(token);
            postings.add(tokenPostings);
        }

        if (progressCallback != null) {
            progressCallback.accept("search", new StatusUpdate("Collected postings (" + postings.size() + ")", collectPostings.getElapsed()));
        }

        LogTimer sumCommonFiles = new LogTimer(false);
//        if (progressCallback != null) {
//            progressCallback.accept("search", new StatusUpdate("Retaining common files..."));
//        }

        Set<Integer> commonFiles = new HashSet<>(postings.get(0).keySet());
        for (int i = 1; i < postings.size(); i++) {
            commonFiles.retainAll(postings.get(i).keySet());
        }

        if (progressCallback != null) {
            progressCallback.accept("search", new StatusUpdate("Retaining common files finished (" + commonFiles.size() + ")", sumCommonFiles.getElapsed()));
        }

        LogTimer makingHitsTotal = new LogTimer(true);

        List<Hit> result = new ArrayList<>();
        for (int fileId : commonFiles) {
            long countingHits = System.currentTimeMillis();
            List<Integer> base = postings.get(0).get(fileId);
            if (base == null || base.isEmpty()) continue;
//            int matchCount = 0;
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
//                for (int va : intersection) {
//                    int expected = va + offset;
//                    int index = Collections.binarySearch(next, expected);
//                    if (index >= 0) {
//                        temp.add(va); // нашли нужную позицию в следующем токене
//                    }
//                }

                if (temp.isEmpty()) {
                    matched = false;
                    break;
                }

                intersection = temp;
            }
            //tt.log("SearchEngine: pos matching finished");
            Profiler.get("hitsCounting").record(System.currentTimeMillis() - countingHits);
            if (matched) {
                long tt = System.currentTimeMillis();
                int hitCount = intersection.size();
                String path = db.getFilePath(fileId);
                File file = new File(path);
                if (file.exists()) {
                    result.add(new Hit(fileId, path, hitCount));
                }
                Profiler.get("makingHit").record(System.currentTimeMillis() - tt);
            }
        }
//        for (int fileId : commonFiles) {
//            int hitCount = countMatches(postings, fileId);
//            if (hitCount > 0) {
//                String path = db.getFilePath(fileId);
//                File file = new File(path);
//                if (file.exists()) {
//                    result.add(new Hit(fileId, path, hitCount));
//                }
//            }
//        }
        String message = "Search stats";
        for(String metric : stats.keySet()) {
            message += "<br>- " + metric + ": " + stats.get(metric);
        }
        message += "<br>- avg collect positions time: " + Profiler.get("collectPositions").getAverage()
                + "<br>- avg computing hits: " + Profiler.get("hitsCounting").getAverage()
                + "<br>- avg making hit obj: " + Profiler.get("makingHit").getAverage();
        Profiler.clear();
        if (progressCallback != null) {
            progressCallback.accept("search", new StatusUpdate(message));
        }
        if (progressCallback != null) {
            progressCallback.accept("search", new StatusUpdate("Computing hits finished", makingHitsTotal.getElapsed()));
        }
        result.sort(Comparator.comparing(hit -> hit.path.toLowerCase()));
        return result;
    }

    private int countMatches(List<Map<Integer, List<Integer>>> postingsPerToken, int fileId) {
        List<Integer> base = postingsPerToken.get(0).get(fileId);
        if (base == null || base.isEmpty()) return 0;

        int hitCount = 0;

        for (int va : base) {
            boolean match = true;

            for (int i = 1; i < postingsPerToken.size(); i++) {
                List<Integer> next = postingsPerToken.get(i).get(fileId);
                if (next == null || next.isEmpty()) {
                    match = false;
                    break;
                }

                int expected = va + i;
                if (Collections.binarySearch(next, expected) < 0) {
                    match = false;
                    break;
                }
            }

            if (match) hitCount++;
        }

        return hitCount;
    }

}
