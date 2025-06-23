package com.mvd.docsearchmvd.model;

import java.io.File;

public class FileEntry {
    public final File file;
    public final long size;
    public final long lastModified;

    public FileEntry(File file) {
        this.file = file;
        this.size = file.length();
        this.lastModified = file.lastModified();
    }

    public String getPath() {
        return file.getAbsolutePath();
    }

    public String getFileName() { return file.getName(); }
}
