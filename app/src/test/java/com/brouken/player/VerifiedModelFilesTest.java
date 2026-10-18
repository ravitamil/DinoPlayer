package com.brouken.player;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.nio.file.Files;
import static org.junit.Assert.*;

public class VerifiedModelFilesTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    @Test public void rejectsCorruptedModel() throws Exception {
        File file=temporary.newFile(); Files.write(file.toPath(),new byte[]{1,2,3});
        String hash=VerifiedModelFiles.sha256(file);
        VerifiedModelFiles.verify(file,3,hash);
        Files.write(file.toPath(),new byte[]{1,9,3});
        try { VerifiedModelFiles.verify(file,3,hash); fail("Corruption accepted"); } catch(IOException expected) { }
    }
    @Test public void rejectsTruncatedModel() throws Exception {
        File file=temporary.newFile();
        try { VerifiedModelFiles.verify(file,99,VerifiedModelFiles.sha256(file)); fail("Truncation accepted"); } catch(IOException expected) { }
    }
    @Test public void bytePatchPreservesRequestedOriginalRange() throws Exception {
        File input=temporary.newFile(),output=temporary.newFile();
        Files.write(input.toPath(),new byte[]{0,1,2,3,4,5});
        VerifiedModelFiles.patch(input,output,2,5,new ByteArrayInputStream(new byte[]{9}),new ByteArrayInputStream(new byte[]{8,7}));
        assertArrayEquals(new byte[]{9,2,3,4,8,7},Files.readAllBytes(output.toPath()));
    }
    @Test public void invalidPatchRangeCannotReadPastModel() throws Exception {
        File input=temporary.newFile(),output=temporary.newFile();
        try { VerifiedModelFiles.patch(input,output,0,10,new ByteArrayInputStream(new byte[0]),new ByteArrayInputStream(new byte[0])); fail("Invalid patch accepted"); }
        catch(IOException expected) { }
    }
}
