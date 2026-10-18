package com.brouken.player;

import org.junit.Test;
import static org.junit.Assert.*;

public class FolderPathsTest {
    @Test public void primaryFolderRetainsCaseAndDecodesSpaces() {
        assertEquals("/storage/emulated/0/Download/My Videos", FolderPaths.normalize(
                "content://com.android.externalstorage.documents/tree/primary%3ADownload%2FMy%20Videos"));
    }
    @Test public void removableVolumeIsNotLost() {
        assertEquals("/storage/6B58-CF5C/Pictures/Instander", FolderPaths.normalize(
                "content://com.android.externalstorage.documents/tree/6B58-CF5C%3APictures%2FInstander"));
    }
    @Test public void documentUsesItsOwnPathRatherThanTreeRoot() {
        assertEquals("/storage/emulated/0/Download/Movies/clip.mp4", FolderPaths.normalize(
                "content://com.android.externalstorage.documents/tree/primary%3ADownload/document/primary%3ADownload%2FMovies%2Fclip.mp4"));
    }
    @Test public void providerUrisAndPlusCharactersArePreserved() {
        assertEquals("content://media/external/video/media/42", FolderPaths.normalize("content://media/external/video/media/42"));
        assertEquals("/storage/emulated/0/A+B", FolderPaths.normalize(
                "content://com.android.externalstorage.documents/tree/primary%3AA+B"));
    }
    @Test public void scopeRequiresAPathBoundary() {
        assertTrue(FolderPaths.isUnder("/Movies/a.mp4", "/Movies/"));
        assertFalse(FolderPaths.isUnder("/Movies2/a.mp4", "/Movies"));
        assertFalse(FolderPaths.isUnder("/movies/a.mp4", "/Movies"));
        assertFalse(FolderPaths.isUnder("/Movies/a.mp4", ""));
        assertEquals("", FolderPaths.normalize(null));
    }
}
