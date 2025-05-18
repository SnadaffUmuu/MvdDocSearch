package com.mvd.docsearchmvd.model;

public class ProgressUpdate {
    public String fileName;
    public int filesDone;
    public int totalFiles;
    public int percent;
    public long elapsed;

    public ProgressUpdate(String fileName, int filesDone, int totalFiles, long elapsed) {
        this.fileName = fileName;
        this.filesDone = filesDone;
        this.totalFiles = totalFiles;
        this.elapsed = elapsed;
    }

    public int getPercent() {
        return (int)((filesDone * 100.0) / totalFiles);
    }
}
