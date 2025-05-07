package com.mvd.docsearchmvd.search;
public class Hit {
    public final Integer fileId;
    public final int hits;

    public Hit(Integer id, int hits) {
        this.fileId = id;
        this.hits = hits;
    }
}
