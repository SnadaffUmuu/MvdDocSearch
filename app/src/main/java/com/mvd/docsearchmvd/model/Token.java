package com.mvd.docsearchmvd.model;
public class Token {
    public final String text;

    public final byte[] positionsBlob;

    public Token(String text, byte[] positionsBlob) {
        this.text = text;
        this.positionsBlob = positionsBlob;
    }
}