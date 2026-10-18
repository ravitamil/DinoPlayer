package com.brouken.player;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.nio.file.Files;
import static org.junit.Assert.*;

public class CacheFilesTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private File cached(String name,long time) throws Exception {
        File file=temporary.newFile(name); Files.write(file.toPath(),new byte[10]); assertTrue(file.setLastModified(time)); return file;
    }
    @Test public void budgetEvictsOldestAndPreservesRecent() throws Exception {
        File old=cached("old.preview",100000),recent=cached("new.preview",200000);
        CacheFiles.trim(temporary.getRoot(),".preview",10,0);
        assertFalse(old.exists()); assertTrue(recent.exists());
    }
    @Test public void ageEvictsEvenWhenUnderBudget() throws Exception {
        File old=cached("old.srt",100000);
        CacheFiles.trim(temporary.getRoot(),".srt",1000,200000);
        assertFalse(old.exists());
    }
    @Test public void unrelatedFilesAndDirectoriesAreUntouched() throws Exception {
        File file=cached("video.mp4",100000),directory=temporary.newFolder("models.preview");
        CacheFiles.trim(temporary.getRoot(),".preview",0,Long.MAX_VALUE);
        assertTrue(file.exists()); assertTrue(directory.isDirectory());
    }
}
