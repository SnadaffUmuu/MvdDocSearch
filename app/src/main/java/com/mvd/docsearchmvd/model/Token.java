package com.mvd.docsearchmvd.model;
public class Token {
    public final String text;
//    public final String positions;
    public final byte[] positionsBlob;

//    public Token(String text, String positions, byte[] positionsBlob) {
//        this.text = text;
//        this.positions = positions;
//        this.positionsBlob = positionsBlob;
//    }

    public Token(String text, byte[] positionsBlob) {
        this.text = text;
        this.positionsBlob = positionsBlob;
    }
}