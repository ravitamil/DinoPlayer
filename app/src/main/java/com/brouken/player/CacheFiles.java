package com.brouken.player;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;

/** Bounded, disposable files only; never traverses into media folders or subtitle packs. */
public final class CacheFiles {
    private CacheFiles() { }
    public static void trim(File directory, String suffix, long budget, long oldestAllowed) {
        File[] files=directory.listFiles(file -> file.isFile() && file.getName().endsWith(suffix));
        if(files==null)return;
        Arrays.sort(files, (first, second) -> Long.compare(first.lastModified(), second.lastModified()));
        long bytes=0;
        for(File file:files) bytes+=file.length();
        for(File file:files) {
            if(file.lastModified()<oldestAllowed || bytes>budget) {
                long size=file.length();
                if(file.delete())bytes-=size;
            }
        }
    }
    public static void clearFlat(File directory) {
        File[] files=directory.listFiles();
        if(files!=null)for(File file:files)if(file.isFile())file.delete();
    }
}
