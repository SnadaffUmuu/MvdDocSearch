package com.mvd.docsearchmvd.indexer;
public class Token {
    public final String text;
    public final String positions;

    public Token(String text, String positions) {
        this.text = text;
        this.positions = positions;
    }
}