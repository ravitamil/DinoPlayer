package com.brouken.player;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;

public class CaptionCheckpointTest {
    @Rule public TemporaryFolder temporary=new TemporaryFolder();
    private List<SubtitleTimeline.Cue> cue() { return Collections.singletonList(new SubtitleTimeline.Cue(8200,9000,"Saved caption")); }
    @Test public void resumePreservesOutOfOrderChunksAndSilence() throws Exception {
        File file=temporary.newFile();CaptionCheckpoint.append(file,1,cue());CaptionCheckpoint.append(file,0,Collections.emptyList());
        List<CaptionCheckpoint.Chunk> restored=CaptionCheckpoint.read(file,3);assertEquals(2,restored.size());assertEquals(1,restored.get(0).index);assertEquals("Saved caption",restored.get(0).cues.get(0).text);assertTrue(restored.get(1).cues.isEmpty());
    }
    @Test public void tornRecordIsTruncatedBeforeAppendingRetry() throws Exception {
        File file=temporary.newFile();CaptionCheckpoint.append(file,1,cue());long good=file.length();
        try(FileOutputStream output=new FileOutputStream(file,true)) { output.write(new byte[]{0x44,0x49,0x4e}); }
        assertEquals(1,CaptionCheckpoint.read(file,3).size());assertEquals(good,file.length());
        CaptionCheckpoint.append(file,2,cue());assertEquals(2,CaptionCheckpoint.read(file,3).size());
    }
    @Test public void mismatchedDurationCannotRestoreInvalidChunks() throws Exception {
        File file=temporary.newFile();CaptionCheckpoint.append(file,10,cue());assertTrue(CaptionCheckpoint.read(file,2).isEmpty());assertEquals(0,file.length());
    }
    @Test public void duplicateChunkIsNotReplayedTwice() throws Exception {
        File file=temporary.newFile();CaptionCheckpoint.append(file,1,cue());long good=file.length();CaptionCheckpoint.append(file,1,cue());assertEquals(1,CaptionCheckpoint.read(file,3).size());assertEquals(good,file.length());
    }
    @Test public void malformedJournalDoesNotBlockRetry() throws Exception {
        File file=temporary.newFile();try(FileWriter out=new FileWriter(file)) { out.write("corrupt data"); }
        assertTrue(CaptionCheckpoint.read(file,1).isEmpty());assertEquals(0,file.length());
    }
}
