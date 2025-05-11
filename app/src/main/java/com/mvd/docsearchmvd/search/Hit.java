package com.mvd.docsearchmvd.search;
public class Hit {
    public final Integer fileId;
    public final int hits;
    public final String path;

    public Hit(Integer id, String path, int hits) {
        this.fileId = id;
        this.path = path;
        this.hits = hits;
    }
}
