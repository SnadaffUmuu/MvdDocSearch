package com.mvd.docsearchmvd.model;

public class StatusUpdate {
    public String stage;
    public String stageElapsed;

    public StatusUpdate(String stage, String stageElapsed) {
        this.stage = stage;
        this.stageElapsed = stageElapsed;
    }

    public StatusUpdate(String stage) {
        this.stage = stage;
    }
}

