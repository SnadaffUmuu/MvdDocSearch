package com.mvd.docsearchmvd.model;
public class ProgressUpdate {

    public String fileName;

    public int filesDone;

    public int totalFiles;

    public String elapsed;

    public int percent;

    public ProgressUpdate(String fileName, int filesDone, int totalFiles, String elapsed) {
        this.fileName = fileName;
        this.filesDone = filesDone;
        this.totalFiles = totalFiles;
        this.elapsed = elapsed;
        this.percent = (totalFiles == 0) ? 0 : (int)((filesDone * 100.0) / totalFiles);
    }

}
